package org.openmrs.module.labonfhir.api.event;

import javax.jms.Message;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import lombok.Setter;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.LocationAttribute;
import org.openmrs.LocationAttributeType;
import org.openmrs.api.LocationService;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.fhir2.api.FhirLocationService;
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

	private static final String MFL_LOCATION_IDENTIFIER_URI = "http://moh.bw.org/ext/location/identifier/mfl";

	private static final String MFL_LOCATION_ATTRIBUTE_TYPE_UUID = "8a845a89-6aa5-4111-81d3-0af31c45c002";

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
	FhirLocationService fhirLocationService;

	@Autowired
	private FhirTaskService fhirTaskService;

	@Autowired
	private LabOnFhirService labOnFhirService;

	@Autowired
	private LocationService locationService;

    @Override
	public void onMessage(Message message) {
		log.trace("Received message {}", message);

		Daemon.runInDaemonThread(() -> {
			try {
				processMessage(message);
			} catch (Exception e) {
				log.error("Failed to update the user's last viewed patients property", e);
			}
		}, daemonToken);
	}

	public abstract void processMessage(Message message);

	public Bundle createLabBundle(Task task) {
		TokenAndListParam uuid = new TokenAndListParam().addAnd(new TokenParam(task.getIdElement().getIdPart()));
		HashSet<Include> includes = new HashSet<>();
		includes.add(new Include("Task:patient"));
		includes.add(new Include("Task:owner"));
		includes.add(new Include("Task:encounter"));
		includes.add(new Include("Task:based-on"));
		includes.add(new Include("Task:location"));
		includes.add(new Include("Task:requester"));

		IBundleProvider labBundle = fhirTaskService.searchForTasks(new TaskSearchParams(null, null, null, uuid, null, null, includes));

		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		List<IBaseResource> labResources = labBundle.getAllResources();
		if (!task.getLocation().isEmpty()) {
			labResources.add(fhirLocationService.get(FhirUtils.referenceToId(task.getLocation().getReference()).get()));
		}

		if (!task.getRequester().isEmpty()) {
			labResources.add(fhirLocationService.get(FhirUtils.referenceToId(task.getRequester().getReference()).get()));
		}

		for (IBaseResource r : labResources) {
			Resource resource = (Resource) r;
			
			// if resource is location, add MFL Code as location identifier
			// TODO: Short term:  Make the MFL Location attribute type to be a Global config variable
			// TODO: Long term: Make the location translator pick and enrich the MFL Code as appropriate
			
			Bundle.BundleEntryComponent component = transactionBundle.addEntry();
			if (resource instanceof Location || resource instanceof Organization) {
				org.openmrs.Location openmrsLocation = locationService.getLocationByUuid(resource.getId());
				if (openmrsLocation != null) {
					LocationAttributeType mflLocationAttributeType = locationService.getLocationAttributeTypeByUuid(MFL_LOCATION_ATTRIBUTE_TYPE_UUID);
					Collection<LocationAttribute> locationAttributeTypes = openmrsLocation.getActiveAttributes();
					if (!locationAttributeTypes.isEmpty()) {
						locationAttributeTypes.stream().filter(locationAttribute -> locationAttribute.getAttributeType().equals(mflLocationAttributeType)).findFirst().ifPresent(locationAttribute -> {
							String mflCode = (String) locationAttribute.getValue();

							Identifier mflIdentifier = new Identifier();
							mflIdentifier.setSystem(MFL_LOCATION_IDENTIFIER_URI); 
							mflIdentifier.setValue(mflCode);

							if (resource instanceof Organization) {
								Organization organization = (Organization) resource;
								organization.addIdentifier(mflIdentifier);
								component.setResource(organization);
							} else {
								Location location = (Location) resource;
								location.addIdentifier(mflIdentifier);
								component.setResource(location);
							}
						});
					}
				}
			} else {
				component.setResource(resource);	
			}

			component.getRequest().setUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
			        .setMethod(Bundle.HTTPVerb.PUT);

		}
		return transactionBundle;
	}

	protected void sendTask(Task task) {
		if (task != null) {
			if (config.getActivateFhirPush()) {
				Bundle labBundle = createLabBundle(task);
				try {
					client.transaction().withBundle(labBundle).execute();
				} catch (Exception e) {
					saveFailedTask(task.getIdElement().getIdPart(), e.getMessage());
					log.error("Failed to send Task with UUID " + task.getIdElement().getIdPart(), e);
				}
				log.debug(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(labBundle));
			}
		}
	}

	private void saveFailedTask(String taskUuid, String error) {
		FailedTask failedTask = new FailedTask();
		failedTask.setError(error);
		failedTask.setIsSent(false);
		failedTask.setTaskUuid(taskUuid);
		labOnFhirService.saveOrUpdateFailedTask(failedTask);
	}
}
