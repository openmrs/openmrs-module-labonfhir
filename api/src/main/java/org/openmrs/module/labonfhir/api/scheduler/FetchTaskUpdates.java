package org.openmrs.module.labonfhir.api.scheduler;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import ca.uhn.fhir.rest.gclient.IQuery;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.FhirTask;
import org.openmrs.module.fhir2.api.FhirDiagnosticReportService;
import org.openmrs.module.fhir2.api.FhirObservationService;
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

	private IGenericClient client;

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

	@Autowired
	FhirObservationService observationService;

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

			client = ctx.newRestfulGenericClient(config.getOpenElisUrl());

			// Get List of Tasks that belong to this instance and update them
			updateTasksInBundle(client.search().forResource(Task.class).where(Task.IDENTIFIER.hasSystemWithAnyCode(
					FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER)).returnBundle(Bundle.class).execute());
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
		// List of tasks that have been updated
		List<Task> updatedTasks = new ArrayList<>();

		for (Iterator tasks = taskBundle.getEntry().iterator(); tasks.hasNext(); ) {
			String openmrsTaskUuid = null;

			try{
				// Read incoming OpenElis task
				Task openelisTask = (Task)((Bundle.BundleEntryComponent)tasks.next()).getResource();
				openmrsTaskUuid = openelisTask.getIdentifierFirstRep().getValue();

				// Find original openmrs task using Identifier
				Task openmrsTask = taskService.getTaskByUuid(openmrsTaskUuid);

				// Only update if matching OpenMRS Task found
				if(openmrsTask != null) {
					// Handle status
					// openmrsTask.setStatus(openelisTask.getStatus());

					// Handle output
					if (openelisTask.hasOutput()) {
						// openmrsTask.setOutput(openelisTask.getOutput());
						openmrsTask.setOutput(updateOutput(openelisTask.getOutput()));
					}

					// Save Task
					updatedTasks.add(taskService.updateTask(openmrsTaskUuid, openmrsTask));
				}
			} catch (Exception e) {
				log.error("Could not save task " + openmrsTaskUuid + ":" + e.toString() + getStackTrace(e));
			}
		}
		return updatedTasks;
	}

	private List<Task.TaskOutputComponent> updateOutput(List<Task.TaskOutputComponent> output) {
		List<Task.TaskOutputComponent> outputList = Collections.EMPTY_LIST;

		if(!output.isEmpty())
		{
			// Save each output entry
			for(Iterator outputRefI = output.stream().iterator(); outputRefI.hasNext(); ) {
				Task.TaskOutputComponent outputRef = (Task.TaskOutputComponent) outputRefI.next();

				// Assume each output ref is a DiagnosticReport for now
				// if(outputRef.getType() == DiagnosticReport.class) {}

				// Set UUIDs for openmrs and openelis DiagnosticReports
				String openelisUuid = ((Reference) outputRef.getValue()).getReferenceElement().getIdPart();
				String openmrsUuid = client.getServerBase() + "/" + openelisUuid;

				// Get Diagnostic Report and associated Observations (using include)
				Bundle diagnosticReportBundle = client.search().forResource(DiagnosticReport.class)
						.where(new TokenClientParam("_id").exactly().code(openelisUuid))
						.include(DiagnosticReport.INCLUDE_RESULT).include(DiagnosticReport.INCLUDE_SUBJECT)
						.returnBundle(Bundle.class).execute();

				DiagnosticReport diagnosticReport = (DiagnosticReport) diagnosticReportBundle.getEntryFirstRep()
						.getResource();

				List<Observation> results = diagnosticReport.getResult().stream().map(r -> (Observation) r.getResource())
						.collect(Collectors.toList());

				// Init empty list to hold OpenMRS versions of Observations in DiagnosticReport.result
				List<Observation> openmrsResults = Collections.EMPTY_LIST;

				// Create / Update result Observations
				for (Observation result : results) {
					String openmrsObservationUuid = client.getServerBase() + "/" + result.getIdElement().getIdPart();

					Observation openmrsObservation = observationService.getObservationByUuid(openmrsObservationUuid);

					if (openmrsObservation == null) {
						// Create
						openmrsObservation = result.copy();
						openmrsObservation.setId(openmrsObservationUuid);

						// Fix for missing Datetime
						if(openmrsObservation.getEffectiveDateTimeType().isEmpty()){
							openmrsObservation.setEffective(new DateTimeType().setValue(new Date()));
						}
					} else {
						// Update
					}
					openmrsResults.add(observationService.saveObservation(openmrsObservation));
				}

				// Create / Update Diagnostic Report
				DiagnosticReport openmrsDiagnosticReport = diagnosticReportService.getDiagnosticReportByUuid(openmrsUuid);

				if (openmrsDiagnosticReport == null) {
					// Create
					openmrsDiagnosticReport = diagnosticReport.copy();
					openmrsDiagnosticReport.setId(openmrsUuid);
					diagnosticReportService.saveDiagnosticReport(openmrsDiagnosticReport);
				} else {
					// Update
					diagnosticReport.copyValues(openmrsDiagnosticReport);
					openmrsDiagnosticReport.setId(openmrsUuid);
					diagnosticReportService
							.updateDiagnosticReport(openmrsUuid, openmrsDiagnosticReport);
				}

				outputList.add((new Task.TaskOutputComponent())
						.setValue((new Reference()
								.setType(FhirConstants.DIAGNOSTIC_REPORT)
								.setReference(FhirConstants.DIAGNOSTIC_REPORT+"/"+openmrsUuid))));
			}
		}

		return outputList;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
