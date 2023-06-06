package org.openmrs.module.labonfhir.api.scheduler;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.api.event.OrderCreationListener;
import org.openmrs.module.labonfhir.api.model.FailedTask;
import org.openmrs.module.labonfhir.api.service.LabOnFhirService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;

@Component
public class RetryFailedTasks extends AbstractTask implements ApplicationContextAware {
    private static Log log = LogFactory.getLog(RetryFailedTasks.class);

    private static ApplicationContext applicationContext;
    
    @Autowired
    @Qualifier("labOrderFhirClient")
    private IGenericClient client;
    
    @Autowired
    private FhirTaskService fhirTaskService;
    
    @Autowired
    private LabOnFhirService labOnFhirService;

    @Autowired
    @Qualifier("labOrderListener") 
    private  OrderCreationListener orderCreationListener;

    @Autowired
	@Qualifier("fhirR4")
	private FhirContext ctx;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    @Override
    public void execute() {
        log.info("Executing Retry Failed tasks");
        try {
            applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
        }
        catch (Exception e) {}

        retrySendingFailedTasks();
    }
    
    private void retrySendingFailedTasks() {
        List<FailedTask> failedTasks = labOnFhirService.getAllFailedTasks(false) ;
        
        failedTasks.forEach(failedTask -> {
             Task task = fhirTaskService.get(failedTask.getTaskUuid());
            try {
                Bundle labBundle = orderCreationListener.createLabBundle(task);
                client.transaction().withBundle(labBundle).execute();
                failedTask.setIsSent(true);
                labOnFhirService.saveOrUpdateFailedTask(failedTask);
                log.info("Resent Failed task:" + failedTask.getTaskUuid());
                log.debug(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(labBundle));
            }
            catch (Exception e) {
                log.error(e);
            }
        });
        
    }
}
