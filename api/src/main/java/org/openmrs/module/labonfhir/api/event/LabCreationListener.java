package org.openmrs.module.labonfhir.api.event;

import javax.jms.Message;

import java.util.HashSet;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.Setter;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.fhir2.api.FhirEncounterService;
import org.openmrs.module.fhir2.api.FhirLocationService;
import org.openmrs.module.fhir2.api.FhirPractitionerService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.util.FhirUtils;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.model.FailedTask;
import org.openmrs.module.labonfhir.api.service.LabOnFhirService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.openmrs.module.fhir2.api.search.param.TaskSearchParams;

public abstract class LabCreationListener implements EventListener {

	private static final Logger log = LoggerFactory.getLogger(EncounterCreationListener.class);

	@Setter
	private DaemonToken daemonToken;

	@Autowired
	@Qualifier("labOrderFhirClient")
	private IGenericClient client;

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	@Qualifier("fhirR4")
	private FhirContext ctx;

	@Autowired
	FhirLocationService fhirLocationService ;

	@Autowired
	private FhirTaskService fhirTaskService;

	@Autowired
	private LabOnFhirService labOnFhirService ;

	@Autowired
	private FhirPractitionerService fhirPractitionerService;

	@Autowired
	private FhirEncounterService fhirEncounterService;

	@Override
	public void onMessage(Message message) {
		log.trace("Received message {}", message);

		Daemon.runInDaemonThread(() -> {
			try {
				processMessage(message);
			}
			catch (Exception e) {
				log.error("Failed to update the user's last viewed patients property", e);
			}
		}, daemonToken);
	}

	public abstract void processMessage(Message message);

	public Bundle createLabBundle(Task task) {
		TokenAndListParam uuid = new TokenAndListParam().addAnd(new TokenParam(task.getIdElement().getIdPart()));
		HashSet<Include> includes = new HashSet<>();
		includes.add(new Include("Task:patient"));
		//includes.add(new Include("Task:owner"));
		//includes.add(new Include("Task:encounter"));
		includes.add(new Include("Task:requester"));
		includes.add(new Include("Task:based-on"));
		//includes.add(new Include("Task:location"));

		IBundleProvider labBundle = fhirTaskService.searchForTasks(new TaskSearchParams(null, null , null ,null, null, uuid, null, null, includes));

		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		List<IBaseResource> labResources = labBundle.getAllResources();
		// if (!task.getLocation().isEmpty()) {
		// 	labResources.add(fhirLocationService.get(FhirUtils.referenceToId(task.getLocation().getReference()).get()));
		// }
		// Add task.requester as practiioner
		if (!task.getRequester().isEmpty()) {
			labResources.add(fhirPractitionerService.get(FhirUtils.referenceToId(task.getRequester().getReference()).get()));
		}

		//Add task.requester as practiioner extracted from task.encounter
		if (!task.getEncounter().isEmpty()) {
			org.hl7.fhir.r4.model.Encounter encounter = fhirEncounterService.get(FhirUtils.referenceToId(task.getEncounter().getReference()).get());
			encounter.getParticipant().forEach(participant -> {
				if (participant.getIndividual().getReference() != null) {
					labResources.add(fhirPractitionerService.get(FhirUtils.referenceToId(participant.getIndividual().getReference()).get()));
				}
			});
		}

		for (IBaseResource r : labResources) {
			Resource resource = (Resource) r;
			Bundle.BundleEntryComponent component = transactionBundle.addEntry();
			component.setFullUrl("urn:uuid:" + resource.getIdElement().getIdPart());

			if (resource instanceof Task) {
				org.hl7.fhir.r4.model.Reference location = ((Task) resource).getLocation();
				org.hl7.fhir.r4.model.Location fhirLocation = fhirLocationService.get(FhirUtils.referenceToId(location.getReference()).get());
				org.hl7.fhir.r4.model.Reference locationRef = new org.hl7.fhir.r4.model.Reference("organization?identifier=http://moh.bw.org/ext/identifier/mfl-code" + "|" + fhirLocation.getIdentifierFirstRep().getValue());
				Task taskResource = (Task) resource;
				taskResource.setLocation(locationRef);

				org.hl7.fhir.r4.model.Encounter encounter = fhirEncounterService.get(FhirUtils.referenceToId(task.getEncounter().getReference()).get());
				encounter.getLocation().forEach(encounterLocation -> {
					org.hl7.fhir.r4.model.Location fhirEncounterLocation = fhirLocationService.get(FhirUtils.referenceToId(encounterLocation.getLocation().getReference()).get());
					org.hl7.fhir.r4.model.Reference organizationRef = new org.hl7.fhir.r4.model.Reference("organization?identifier=http://moh.bw.org/ext/identifier/mfl-code" + "|" + fhirEncounterLocation.getIdentifierFirstRep().getValue());
					taskResource.setOwner(organizationRef);
				});
				component.setResource(taskResource);
				
			} else if (resource instanceof ServiceRequest) {
				// remove the encounter reference from the service request
				component.setResource(((ServiceRequest) resource).setEncounter(null));
			} else {
				component.setResource(resource);
			}

			// if patient bundle, set url with identifier system and value 
			if (resource.fhirType().equals("Patient")) {
				for (org.hl7.fhir.r4.model.Identifier identifier : ((org.hl7.fhir.r4.model.Patient) resource).getIdentifier()) {
					if (identifier.getSystem().equals("http://moh.bw.org/ext/identifier/omang")) {
						component.getRequest().setUrl(resource.fhirType() + "?identifier=" + identifier.getSystem() + "|" + identifier.getValue())
								.setMethod(Bundle.HTTPVerb.PUT);
						break;
					}
					if (identifier.getSystem().equals("http://moh.bw.org/ext/identifier/bcn")) {
						component.getRequest().setUrl(resource.fhirType() + "?identifier=" + identifier.getSystem() + "|" + identifier.getValue())
								.setMethod(Bundle.HTTPVerb.PUT);
						break;
					}
					if (identifier.getSystem().equals("http://moh.bw.org/ext/identifier/ppn")) {
						component.getRequest().setUrl(resource.fhirType() + "?identifier=" + identifier.getSystem() + "|" + identifier.getValue())
								.setMethod(Bundle.HTTPVerb.PUT);
						break;
					}
				}
			} else {
				component.getRequest().setUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
						.setMethod(Bundle.HTTPVerb.PUT);
			}
		}
		return transactionBundle;
	}

	protected void sendTask(Task task) {
		if (task != null) {
			if (config.getActivateFhirPush()) {
				Bundle labBundle = createLabBundle(task);
				try {
					client.transaction().withBundle(labBundle).execute();
				}
				catch (Exception e) {
					saveFailedTask(task.getIdElement().getIdPart(), e.getMessage());
					log.error("Failed to send Task with UUID " + task.getIdElement().getIdPart(), e);
			}
				log.debug(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(labBundle));
			}
		}
	}

	private void saveFailedTask(String taskUuid ,String error) {
		FailedTask failedTask = new FailedTask();
		failedTask.setError(error);
		failedTask.setIsSent(false);
		failedTask.setTaskUuid(taskUuid);
		labOnFhirService.saveOrUpdateFailedTask(failedTask);
	}
}
