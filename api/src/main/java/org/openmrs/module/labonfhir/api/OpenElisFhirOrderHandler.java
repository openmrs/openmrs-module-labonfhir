package org.openmrs.module.labonfhir.api;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Order;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenElisFhirOrderHandler {
	
	@Autowired
	private LabOnFhirConfig config;
	
	@Autowired
	private FhirTaskService taskService;
	
	public Task createOrder(Encounter encounter) throws OrderCreationException {
		if(encounter.getOrders().isEmpty()){
           return null ;
		}
		// Create References
		List<Reference> basedOnRefs = encounter.getOrders().stream().map(order -> {
			AtomicReference<Order> orders = new AtomicReference<>();
			orders.set(order);
			
			if (orders.get() != null) {
				return newReference(orders.get().getUuid(), FhirConstants.SERVICE_REQUEST);
			} else {
				return null;
			}
		}).collect(Collectors.toList());
		
		Reference forReference = newReference(encounter.getPatient().getUuid(), FhirConstants.PATIENT);
		
		Reference ownerRef = newReference(config.getOpenElisUserUuid(), FhirConstants.PRACTITIONER);
		
		Reference encounterRef = newReference(encounter.getUuid(), FhirConstants.ENCOUNTER);

		Optional<EncounterProvider> requesterProvider = encounter.getActiveEncounterProviders().stream().findFirst();

		Reference requesterRef = requesterProvider.isPresent() ? newReference(requesterProvider.get().getUuid(), FhirConstants.PRACTITIONER) : null;
		
		// Create Task Resource for given Order
		Task newTask = new Task();
		newTask.setStatus(Task.TaskStatus.REQUESTED);
		newTask.setIntent(Task.TaskIntent.ORDER);
		newTask.setBasedOn(basedOnRefs);
		newTask.setFor(forReference);
		newTask.setOwner(ownerRef);
		newTask.setEncounter(encounterRef);

		if(!encounter.getActiveEncounterProviders().isEmpty()){
			newTask.setRequester(requesterRef);
		}

		// Save the new Task Resource
		try {
			newTask = taskService.create(newTask);
		}
		catch (DAOException e) {
			throw new OrderCreationException("Exception occurred while creating task for encounter " + encounter.getId());
		}
		return newTask;
	}
	
	private Reference newReference(String uuid, String type) {
		return new Reference().setReference(type + "/" + uuid).setType(type);
	}
	
}
