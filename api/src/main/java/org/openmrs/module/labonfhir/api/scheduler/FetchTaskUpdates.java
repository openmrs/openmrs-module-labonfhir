package org.openmrs.module.labonfhir.api.scheduler;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.codesystems.TaskStatus;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirDiagnosticReportService;
import org.openmrs.module.fhir2.api.FhirObservationService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.dao.FhirObservationDao;
import org.openmrs.module.fhir2.api.translators.ObservationReferenceTranslator;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FetchTaskUpdates extends AbstractTask implements ApplicationContextAware {

	private static Log log = LogFactory.getLog(FetchTaskUpdates.class);

	private static ApplicationContext applicationContext;

	private static String LOINC_SYSTEM = "http://loinc.org";

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	private IGenericClient client;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	private FhirDiagnosticReportService diagnosticReportService;

	@Autowired
	FhirObservationDao observationDao;

	@Autowired
	FhirObservationService observationService;

	@Autowired
	ObservationReferenceTranslator observationReferenceTranslator;

	@Autowired
	@Qualifier("sessionFactory")
	SessionFactory sessionFactory;

	@Override
	public void execute() {

		try {
			applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		} catch (Exception e) {
			// return;
		}

		if (!config.isLisEnabled()) {
			return;
		}

		try {
			// Get List of Tasks that belong to this instance and update them
			updateTasksInBundle(client.search().forResource(Task.class)
					.where(Task.IDENTIFIER.hasSystemWithAnyCode(FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER))
					.where(Task.STATUS.exactly().code(TaskStatus.COMPLETED.toCode())).returnBundle(Bundle.class)
					.execute());
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

	private void updateTasksInBundle(Bundle taskBundle) {

		for (Iterator tasks = taskBundle.getEntry().iterator(); tasks.hasNext();) {
			String openmrsTaskUuid = null;

			try {
				// Read incoming LIS Task
				Task openelisTask = (Task) ((Bundle.BundleEntryComponent) tasks.next()).getResource();
				openmrsTaskUuid = openelisTask.getIdentifierFirstRep().getValue();

				// Find original openmrs task using Identifier
				Task openmrsTask = taskService.get(openmrsTaskUuid);

				// Only update if matching OpenMRS Task found
				if (openmrsTask != null) {
					// Handle status
					openmrsTask.setStatus(openelisTask.getStatus());

					Boolean taskOutPutUpdated = false;
					if (openelisTask.hasOutput()) {
						// openmrsTask.setOutput(openelisTask.getOutput());
						taskOutPutUpdated = updateOutput(openelisTask.getOutput(), openmrsTask);
					}
					if (taskOutPutUpdated) {
						taskService.update(openmrsTaskUuid, openmrsTask);
					}
				}
			} catch (Exception e) {
				log.error("Could not save task " + openmrsTaskUuid + ":" + e.toString() + getStackTrace(e));
			}
		}
	}

	private Boolean updateOutput(List<Task.TaskOutputComponent> output, Task openmrsTask) {

		Reference encounterReference = openmrsTask.getEncounter();
		List<Reference> basedOn = openmrsTask.getBasedOn();
		List<String> allExistingLoincCodes = new ArrayList<>();
		Boolean taskOutPutUpdated = false;
		// openmrsTask.getOutput().stream().map(ouput -> ouput.getType().getCoding());
		openmrsTask.getOutput().forEach(out -> {
			out.getType().getCoding().stream().filter(coding -> coding.hasSystem())
					.filter(coding -> coding.getSystem().equals(LOINC_SYSTEM))
					.forEach(coding -> {
						allExistingLoincCodes.add(coding.getCode());
					});
		});
		if (!output.isEmpty()) {
			// Save each output entry
			for (Iterator outputRefI = output.stream().iterator(); outputRefI.hasNext();) {
				Task.TaskOutputComponent outputRef = (Task.TaskOutputComponent) outputRefI.next();
				String openelisDiagnosticReportUuid = ((Reference) outputRef.getValue()).getReferenceElement()
						.getIdPart();
				// Get Diagnostic Report and associated Observations (using include)
				Bundle diagnosticReportBundle = client.search().forResource(DiagnosticReport.class)
						.where(new TokenClientParam("_id").exactly().code(openelisDiagnosticReportUuid))
						.include(DiagnosticReport.INCLUDE_RESULT).include(DiagnosticReport.INCLUDE_SUBJECT)
						.returnBundle(Bundle.class).execute();

				DiagnosticReport diagnosticReport = (DiagnosticReport) diagnosticReportBundle.getEntryFirstRep()
						.getResource();
				Coding diagnosticReportCode = diagnosticReport.getCode().getCodingFirstRep();
				if (diagnosticReportCode.getSystem().equals(LOINC_SYSTEM)) {
					List<Reference> results = new ArrayList<>();
					if (!allExistingLoincCodes.contains(diagnosticReportCode.getCode())) {
						// save Observation
						for (Bundle.BundleEntryComponent entry : diagnosticReportBundle.getEntry()) {
							if (entry.hasResource()) {
								if (ResourceType.Observation.equals(entry.getResource().getResourceType())) {
									Observation newObs = (Observation) entry.getResource();
									newObs.setEncounter(encounterReference);
									newObs.setBasedOn(basedOn);
									newObs = observationService.create(newObs);
									Reference obsRef = new Reference();
									obsRef.setReference(
											ResourceType.Observation + "/" + newObs.getIdElement().getIdPart());
									results.add(obsRef);
								}
							}
						}
						diagnosticReport.setResult(results);
						diagnosticReport.setEncounter(encounterReference);
						diagnosticReport = diagnosticReportService.create(diagnosticReport);
						openmrsTask.addOutput().setValue(
								new Reference().setType(FhirConstants.DIAGNOSTIC_REPORT)
										.setReference(diagnosticReport.getIdElement().getIdPart()))
								.setType(diagnosticReport.getCode());
						taskOutPutUpdated = true;
					}
				}
			}
		}
		return taskOutPutUpdated;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
