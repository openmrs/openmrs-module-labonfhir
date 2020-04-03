package org.openmrs.module.labonfhir.api;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenElisFhirOrderHandler {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private FhirTaskService taskService;

	public void createOrder(Encounter encounter) throws OrderCreationException {
		AtomicReference<Obs> orderObs = new AtomicReference<>();

		// Filter and Sort Obs that are Test Orders
		//encounter.getObs().stream().filter(config.isTestOrder()).findFirst().ifPresent(orderObs::set);
		encounter.getObs().stream().findFirst().ifPresent(orderObs::set);

		if (orderObs.get() == null) {
			throw new OrderCreationException("Could not find order for encounter " + encounter);
		}

		// Create basedOn Reference to Order/ServiceRequest
		Reference basedOnRef = new Reference().setReference(orderObs.get().getUuid()).setType(FhirConstants.SERVICE_REQUEST);

		// Create for Reference
		Reference forReference = new Reference().setType(FhirConstants.PATIENT).setReference(encounter.getPatient().getUuid());

		// Create owner Reference
		Reference ownerRef = new Reference().setType(FhirConstants.PATIENT).setReference(config.getOpenElisUserUuid());

		// Create encounter Reference
		Reference encounterRef = new Reference().setType(FhirConstants.ENCOUNTER).setReference(encounter.getUuid());

		// Create Task Resource for given Order
		Task newTask = new Task();
		newTask.setStatus(Task.TaskStatus.REQUESTED);
		newTask.setIntent(Task.TaskIntent.ORDER);
		newTask.setBasedOn(Collections.singletonList(basedOnRef));
		newTask.setFor(forReference);
		newTask.setOwner(ownerRef);
		newTask.setEncounter(encounterRef);

		// Save the new Task Resource
		try {
			taskService.saveTask(newTask);
		} catch (DAOException e) {
			throw new OrderCreationException("Exception occurred while creating task for order " + orderObs.get().getUuid(), e);
		}
	}
}
