package org.openmrs.module.labonfhir.api.scheduler;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.gclient.TokenClientParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.codesystems.TaskStatus;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirDiagnosticReportService;
import org.openmrs.module.fhir2.api.FhirObservationService;
import org.openmrs.module.fhir2.api.FhirPractitionerService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.dao.FhirObservationDao;
import org.openmrs.module.fhir2.api.translators.ObservationReferenceTranslator;
import org.openmrs.module.labonfhir.FhirConfig;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.model.TaskRequest;
import org.openmrs.module.labonfhir.api.service.LabOnFhirService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Setter(AccessLevel.PACKAGE)
public class FetchTaskUpdates extends AbstractTask implements ApplicationContextAware {

	private static Log log = LogFactory.getLog(FetchTaskUpdates.class);

	private static ApplicationContext applicationContext;

	private static String LOINC_SYSTEM = "http://loinc.org";

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	@Qualifier("labOrderFhirConfig")
	private FhirConfig fhirConfig;

	@Autowired
    @Qualifier("fhirR4")
    private FhirContext fhirContext;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	private FhirDiagnosticReportService diagnosticReportService;

	@Autowired
	FhirObservationDao observationDao;

	@Autowired
	FhirObservationService observationService;

	@Autowired
	OrderService orderService;

	@Autowired
	ObservationReferenceTranslator observationReferenceTranslator;

	@Autowired
	@Qualifier("sessionFactory")
	SessionFactory sessionFactory;

	@Autowired
    private LabOnFhirService labOnFhirService;

	@Autowired
    private FhirPractitionerService practitionerService;

	@Override
	public void execute() {
		
		try {
			applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		}
		catch (Exception e) {
			// return;
		}
		
		if (!config.isLisEnabled()) {
			return;
		}
		
		try {
			DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
			Date newDate = new Date();
			ZoneId zone = ZoneId.systemDefault(); 
            ZonedDateTime newZoneDate = newDate.toInstant().atZone(zone);

			Calendar calendar = Calendar.getInstance();
			calendar.setTime(newDate);
			calendar.add(Calendar.YEAR, -5);
			Date fiveYearsAgo = calendar.getTime();
			
			TaskRequest lastRequest = labOnFhirService.getLastTaskRequest();
			String lastRequestDate = dateFormat.format(fiveYearsAgo.toInstant().atZone(zone));
			if (lastRequest != null) {
				lastRequestDate = dateFormat.format(lastRequest.getRequestDate().toInstant().atZone(zone));
			}
			
			String practitionerId = config.getLisUserUuid();

			String currentTime = dateFormat.format(newZoneDate);
			DateRangeParam lastUpdated = new DateRangeParam().setLowerBound(lastRequestDate).setUpperBound(currentTime);
			
			// Get List of Tasks that belong to this instance and update them
			Bundle taskBundle = new Bundle();
			Boolean userExists = true;
			try {
				practitionerService.get(config.getLisUserUuid());
				userExists = true;
			} catch (Exception e) {
				userExists = false;
			}
			if (userExists) {
				taskBundle = fhirConfig.getFhirClient().search().forResource(Task.class)
						.where(Task.IDENTIFIER.hasSystemWithAnyCode(FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER))
						.where(Task.OWNER.hasId(practitionerId))
						.where(Task.STATUS.exactly().codes(
								TaskStatus.COMPLETED.toCode(),
								TaskStatus.ACCEPTED.toCode(),
								TaskStatus.REJECTED.toCode(),
								TaskStatus.CANCELLED.toCode()))
						.lastUpdated(lastUpdated)
						.returnBundle(Bundle.class).execute();
			} else {
				taskBundle = fhirConfig.getFhirClient().search().forResource(Task.class)
						.where(Task.IDENTIFIER.hasSystemWithAnyCode(FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER))
						.where(Task.STATUS.exactly().codes(
								TaskStatus.COMPLETED.toCode(),
								TaskStatus.ACCEPTED.toCode(),
								TaskStatus.REJECTED.toCode(),
								TaskStatus.CANCELLED.toCode()))
						.lastUpdated(lastUpdated)
						.returnBundle(Bundle.class).execute();

			}	
			
			log.debug(fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(taskBundle));
			List<Bundle> taskBundles = new ArrayList<>();
			taskBundles.add(taskBundle);
			//Support FHIR Server Pagination
			while (taskBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
				taskBundle = fhirConfig.getFhirClient().loadPage().next(taskBundle).execute();
				taskBundles.add(taskBundle);
			}
			Boolean tasksUpdated = updateTasksInBundle(taskBundles);
			if (tasksUpdated) {
				TaskRequest request = new TaskRequest();
				request.setRequestDate(newDate);
				labOnFhirService.saveOrUpdateTaskRequest(request);
			}
		}
		catch (Exception e) {
			log.error("ERROR executing FetchTaskUpdates : " + e.toString() + getStackTrace(e));
		}
		
		super.startExecuting();
	}

	@Override
	public void shutdown() {
		log.debug("shutting down FetchTaskUpdates Task");

		this.stopExecuting();
	}

	private Boolean updateTasksInBundle(List<Bundle> taskBundles) {
		Boolean tasksUpdated = false;
		String commentText = "Update Order with remote fhir status : ";
		for (Bundle bundle : taskBundles) {
			for (Iterator tasks = bundle.getEntry().iterator(); tasks.hasNext();) {
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
						if (openelisTask.getStatus().toString().equals(TaskStatus.COMPLETED.toString())) {

							openmrsTask.setStatus(openelisTask.getStatus());
						
							Boolean taskOutPutUpdated = false;
							if (openelisTask.hasOutput()) {
								// openmrsTask.setOutput(openelisTask.getOutput());
								taskOutPutUpdated = updateOutput(openelisTask.getOutput(), openmrsTask);
							}
							if (taskOutPutUpdated) {
								taskService.update(openmrsTaskUuid, openmrsTask);
								tasksUpdated = taskOutPutUpdated;
							}
						}

						if(openelisTask.getStatus().toString().equals(TaskStatus.REJECTED.toString()) ){
							openmrsTask.setStatus(openelisTask.getStatus());
							commentText  = commentText + TaskStatus.REJECTED.toString();
							tasksUpdated = true;
							log.debug("Lab On FHIR : OpenELIS Task Rejected");
						}

						if(openelisTask.getStatus().toString().equals(TaskStatus.CANCELLED.toString()) ){
							openmrsTask.setStatus(openelisTask.getStatus());
							commentText  = commentText + TaskStatus.CANCELLED.toString();
							tasksUpdated = true;
							log.debug("Lab On FHIR : OpenELIS Task Cancelled");
						}
						
						if( openelisTask.getStatus().toString().equals(TaskStatus.ACCEPTED.toString()) ){
							openmrsTask.setStatus(openelisTask.getStatus());
							commentText  = commentText + TaskStatus.ACCEPTED.toString();
							tasksUpdated = true;
							log.debug("Lab On FHIR : OpenELIS Task Accepted");
						}
					}
				}
				catch (Exception e) {
					log.error("Could not save task " + openmrsTaskUuid + ":" + e.toString() + getStackTrace(e));
				}
			}
		}
		return tasksUpdated;
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
			try{
			// Save each output entry
				for (Iterator outputRefI = output.stream().iterator(); outputRefI.hasNext();) {
					Task.TaskOutputComponent outputRef = (Task.TaskOutputComponent) outputRefI.next();
					String openelisDiagnosticReportUuid = ((Reference) outputRef.getValue()).getReferenceElement()
							.getIdPart();
					// Get Diagnostic Report and associated Observations (using include)
					Bundle diagnosticReportBundle = fhirConfig.getFhirClient().search().forResource(DiagnosticReport.class)
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
			}catch(Exception e){}
		}
		return taskOutPutUpdated;
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
