package org.openmrs.module.labonfhir.api.scheduler;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.FhirTask;
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
	private FhirTaskService taskService;

	@Override
	public void execute() {
		FhirContext ctx = null;

		applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		if (!config.isOpenElisEnabled()) {
			return;
		}

		try {
			// TODO: Clean up
			ctx = applicationContext.getBean(FhirContext.class);
			((ApacheRestfulClientFactory)clientFactory).setFhirContext(ctx);
			ctx.setRestfulClientFactory(clientFactory);

			IGenericClient client = ctx.newRestfulGenericClient("http://hapi.fhir.org/baseR4");

			Bundle tasksToUpdate = client.search().forResource(Task.class).where(Task.IDENTIFIER.hasSystemWithAnyCode(
					FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER)).returnBundle(Bundle.class).execute();

			for (Iterator resources = tasksToUpdate.getEntry().iterator(); resources.hasNext(); ) {
				// Update task status and output
				Task toSave = (Task)((Bundle.BundleEntryComponent)resources.next()).getResource();
				String openmrs_uuid = toSave.getId();

				if(toSave.hasIdentifier()) {
					Identifier id = toSave.getIdentifierFirstRep();
					if(id.getSystem() == FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER) {
						openmrs_uuid = id.getValue();
					}
				}

				if(toSave.hasOutput()) {
					// Handle Diagnostic Report?
				}
				try{
					taskService.updateTask(openmrs_uuid, toSave);
				} catch (Exception e) {
					log.error("Could not save task " + openmrs_uuid + ":" + e.toString() + getStackTrace(e));
				}
			}
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

	private Collection<String> getOpenelisTaskUuids() {
		// ReferenceParam ownerRef = new ReferenceParam().setValue(FhirConstants.PRACTITIONER + "/" + config.getOpenElisUserUuid());

		TokenOrListParam status = new TokenOrListParam().add(new TokenParam().setValue(FhirTask.TaskStatus.ACCEPTED.toString())).add(new TokenParam().setValue(FhirTask.TaskStatus.REQUESTED.toString()));

		Collection<Task> openelisTasks = taskService.searchForTasks(null, null, status, null);

		if(!openelisTasks.isEmpty()){
			return openelisTasks.stream().map(Task::getId).collect(Collectors.toList());
		} else {
			return Collections.EMPTY_LIST;
		}
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
