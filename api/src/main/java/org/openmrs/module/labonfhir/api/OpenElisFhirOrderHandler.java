package org.openmrs.module.labonfhir.api;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.FhirReference;
import org.openmrs.module.fhir2.FhirTask;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.dao.FhirTaskDao;
import org.openmrs.module.fhir2.api.translators.TaskTranslator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenElisFhirOrderHandler {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

//	@Autowired
//	private FhirTaskDao taskDao;

	@Autowired
	private TaskTranslator taskTranslator;

	public void createOrder(Encounter encounter) throws OrderCreationException {
		AtomicReference<Obs> orderObs = new AtomicReference<>();

		// Filter and Sort Obs that are Test Orders
		encounter.getObs().stream().filter(config.isTestOrder()).findFirst().ifPresent(orderObs::set);

		if (orderObs.get() == null) {
			throw new OrderCreationException("Could not find order for encounter " + encounter);
		}

		// Create basedOn Reference to Order/ServiceRequest
		FhirReference basedOnRef = new FhirReference();
		basedOnRef.setType(FhirConstants.SERVICE_REQUEST);
		basedOnRef.setReference(orderObs.get().getUuid());

		// Create for Reference
		FhirReference forReference = new FhirReference();
		forReference.setType(FhirConstants.PATIENT);
		forReference.setReference(encounter.getPatient().getUuid());

		// Create owner Reference
		FhirReference ownerRef = new FhirReference();
		ownerRef.setType(FhirConstants.PATIENT);
		ownerRef.setReference(config.getOpenElisUserUuid());

		// Create encounter Reference
		FhirReference encounterRef = new FhirReference();
		encounterRef.setType(FhirConstants.ENCOUNTER);
		ownerRef.setReference(encounter.getUuid());

		// Create Task Resource for given Order
		FhirTask newTask = new FhirTask();
		newTask.setStatus(FhirTask.TaskStatus.REQUESTED);
		newTask.setIntent(FhirTask.TaskIntent.ORDER);
		newTask.setBasedOnReferences(Collections.singleton(basedOnRef));
		newTask.setForReference(forReference);
		newTask.setOwnerReference(ownerRef);
		newTask.setEncounterReference(encounterRef);

		// Translate and save

		// Not needed due to use of `owner` element
		// task.getMeta().addTag("http://fhir.isanteplus.com/R4/ext/lab-destination-valueset", "OpenElis", "OpenElis");

		// Save the new Task Resource
		try {
			return; // taskDao.saveTask(newTask);
		} catch (DAOException e) {
			throw new OrderCreationException("Exception occurred while creating task for order " + orderObs.get().getUuid(), e);
		}
	}
}
