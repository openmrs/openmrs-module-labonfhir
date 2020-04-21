package org.openmrs.module.labonfhir.api.scheduler;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.FhirTask;
import org.openmrs.module.fhir2.api.FhirDiagnosticReportService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FetchTaskUpdates extends AbstractTask implements ApplicationContextAware {

	private static Log log = LogFactory.getLog(FetchTaskUpdates.class);

	private static ApplicationContext applicationContext;

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private IRestfulClientFactory clientFactory;

	@Autowired
	CloseableHttpClient httpClient;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	private FhirDiagnosticReportService diagnosticReportService;
	@Override
	public void execute() {
		FhirContext ctx = null;

		try {
			applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		} catch (Exception e) {
			// return;
		}

		if (!config.isOpenElisEnabled()) {
			return;
		}

		try {
			// TODO: Clean up
			ctx = applicationContext.getBean(FhirContext.class);
			((ApacheRestfulClientFactory)clientFactory).setFhirContext(ctx);

			clientFactory.setHttpClient(httpClient);
			ctx.setRestfulClientFactory(clientFactory);

			IGenericClient client = ctx.newRestfulGenericClient(config.getOpenElisUrl());

			Bundle tasksToUpdate = client.search().forResource(Task.class).where(Task.IDENTIFIER.hasSystemWithAnyCode(
					FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER)).returnBundle(Bundle.class).execute();

			Collection<Task> updatedTasks = updateTasksInBundle(tasksToUpdate);
		} catch (Exception e) {
			log.error("ERROR executing FetchTaskUpdates : " + e.toString() + getStackTrace(e));
		}

		super.startExecuting();
	}

	@Override
	public void shutdown() {
		log.debug("shutting down FetchTaskUpdates Task");

		this.stopExecuting();
	}

	public Collection<Task> updateTasksInBundle(Bundle taskBundle) {
		List<Task> updatedTasks = new ArrayList<>();

		for (Iterator tasks = taskBundle.getEntry().iterator(); tasks.hasNext(); ) {
			// Update task status and output
			Task openelisTask = (Task)((Bundle.BundleEntryComponent)tasks.next()).getResource();

			String openmrsTaskUuid = openelisTask.getIdentifierFirstRep().getValue();
			Task openmrsTask = taskService.getTaskByUuid(openmrsTaskUuid);

			// Handle status
			openmrsTask.setStatus(openelisTask.getStatus());

			if(openelisTask.hasOutput()) {
				openmrsTask.setOutput(openelisTask.getOutput());
				updateOutput(openelisTask.getOutput());
			}

			try{
				updatedTasks.add(taskService.updateTask(openmrsTaskUuid, openmrsTask));
			} catch (Exception e) {
				log.error("Could not save task " + openmrsTaskUuid + ":" + e.toString() + getStackTrace(e));
			}
		}
		return updatedTasks;
	}

	private void updateOutput(List<Task.TaskOutputComponent> output) {
		if(!output.isEmpty())
		{
			for(Iterator outputRefI = output.stream().iterator(); outputRefI.hasNext(); ) {
				Task.TaskOutputComponent outputRef = (Task.TaskOutputComponent) outputRefI.next();
				String uuid = outputRef.getValue().toString();
				diagnosticReportService.getDiagnosticReportByUuid(uuid);
			}
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
