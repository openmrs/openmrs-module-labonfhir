package org.openmrs.module.labonfhir.api;

import java.util.concurrent.atomic.AtomicReference;

import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.translators.TaskTranslator;
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

	@Autowired
	private TaskTranslator taskTranslator;

	public void createOrder(Encounter encounter) throws OrderCreationException {
		AtomicReference<Obs> orderObs = new AtomicReference<>();
		encounter.getObs().stream().filter(config.isTestOrder()).findFirst().ifPresent(orderObs::set);

		if (orderObs.get() == null) {
			throw new OrderCreationException("Could not find order for encounter " + encounter);
		}

		org.openmrs.module.fhir2.FhirTask newTask = new org.openmrs.module.fhir2.FhirTask();
		newTask.setBasedOn("ServiceRequest/" + orderObs.get().getUuid());

		Task task = taskTranslator.toFhirResource(newTask);
		task.getMeta().addTag("http://fhir.isanteplus.com/R4/ext/lab-destination-valueset", "OpenElis", "OpenElis");
		try {
			taskService.saveTask(task);
		} catch (DAOException e) {
			throw new OrderCreationException("Exception occurred while creating task for order " + orderObs.get().getUuid(), e);
		}
	}
}
