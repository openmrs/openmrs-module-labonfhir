package org.openmrs.module.labonfhir.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DecimalType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Quantity;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirObservationService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.springframework.beans.factory.annotation.Autowired;

public class LabOrderHandler {

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	private FhirObservationService observationService;

	public Task createOrder(Order order) throws OrderCreationException {
		//TDO: MAKE THIS A GLOBAL CONFIG
		final String REQUIRED_TESTS_UUIDS = config.getOrderTestUuids(); // GeneXpert
		// Exit if Test Order doesn't contain required tests
		boolean mappedTestsExist = false;
		for (Obs obs : order.getEncounter().getObs()) {
			if (Arrays.stream(REQUIRED_TESTS_UUIDS.split(",")).anyMatch(s -> s.equals(obs.getConcept().getUuid())
					|| (obs.getValueCoded() != null && s.equals(obs.getValueCoded().getUuid())))) {
				mappedTestsExist = true;
			}
		}

		if (!mappedTestsExist && config.filterOrderByTestUuuids()) {
			return null;
		}

		List<Task.ParameterComponent> taskInputs = null;
		if (config.addObsAsTaskInput()) {
			taskInputs = new ArrayList<>();
			for (Obs obs : order.getEncounter().getObs()) {
				Observation observation = observationService.get(obs.getUuid());
				Task.ParameterComponent input = new Task.ParameterComponent();
				input.setType(observation.getCode());
				//this is a temporaly fix because currentl the TASK api supports ony few value types
				//TO DO The OpenMRS task api should support more value types ie ValueQuantity ,valueCodeableConcept , valueboolean
				if (observation.getValue() instanceof CodeableConcept) {
					CodeableConcept concept = (CodeableConcept) observation.getValue();
					input.setValue(new StringType().setValue(concept.getCodingFirstRep().getDisplay()));
				} else if (observation.getValue() instanceof Quantity) {
					Double quantity = ((Quantity) observation.getValue()).getValue().doubleValue();
					DecimalType decimal = new DecimalType();
					decimal.setValue(quantity);
					input.setValue(decimal);
				} else if (observation.getValue() instanceof BooleanType) {
					input.setValue(new StringType().setValue(((BooleanType) observation.getValue()).getValueAsString()));
				} else {
					input.setValue(observation.getValue());
				}
				taskInputs.add(input);
			}
		}
		// Create References
		List<Reference> basedOnRefs = Collections.singletonList(
				newReference(order.getUuid(), FhirConstants.SERVICE_REQUEST));

		Reference forReference = newReference(order.getPatient().getUuid(), FhirConstants.PATIENT);

		Reference ownerRef = newReference(config.getLisUserUuid(), FhirConstants.PRACTITIONER);

		Reference encounterRef = newReference(order.getEncounter().getUuid(), FhirConstants.ENCOUNTER);

		Reference locationRef = null;

		Reference requesterRef = null;
		
		if (order.getEncounter().getLocation() != null) {
			requesterRef = newReference(order.getEncounter().getLocation().getUuid(), FhirConstants.ORGANIZATION);
			locationRef = newReference(order.getEncounter().getLocation().getUuid(), FhirConstants.LOCATION);
		}
		// Optional<EncounterProvider> requesterProvider = order.getEncounter().getActiveEncounterProviders().stream()
		//		.findFirst();

		// Create Task Resource for given Order
		Task newTask = createTask(basedOnRefs, forReference, ownerRef, encounterRef, locationRef,requesterRef ,taskInputs);

		// Save the new Task Resource
		try {
			newTask = taskService.create(newTask);
		}
		catch (DAOException e) {
			throw new OrderCreationException("Exception occurred while creating task for order " + order.getId());
		}
		return newTask;
	}

	private Task createTask(List<Reference> basedOnRefs, Reference forReference, Reference ownerRef,
			Reference encounterRef , Reference locationRef,Reference requesterRef, List<Task.ParameterComponent> taskInputs) {
		Task newTask = new Task();
		newTask.setStatus(Task.TaskStatus.REQUESTED);
		newTask.setIntent(Task.TaskIntent.ORDER);
		newTask.setBasedOn(basedOnRefs);
		newTask.setFor(forReference);
		newTask.setOwner(ownerRef);
		newTask.setEncounter(encounterRef);
		newTask.setRequester(requesterRef);
		newTask.setLocation(locationRef);
		if (taskInputs != null) {
			newTask.setInput(taskInputs);
		}
		return newTask;
	}

	public Task createOrder(Encounter encounter) throws OrderCreationException {
		if (encounter.getOrders().isEmpty()) {
			return null;
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

		Reference ownerRef = newReference(config.getLisUserUuid(), FhirConstants.PRACTITIONER);

		Reference encounterRef = newReference(encounter.getUuid(), FhirConstants.ENCOUNTER);

		Reference locationRef = newReference(encounter.getLocation().getUuid(), FhirConstants.LOCATION);

		// Optional<EncounterProvider> requesterProvider = encounter.getActiveEncounterProviders().stream().findFirst();

		Reference requesterRef = newReference(encounter.getLocation().getUuid(), FhirConstants.ORGANIZATION);

		List<Task.ParameterComponent> taskInputs = null;
		if (config.addObsAsTaskInput()) {
			taskInputs = new ArrayList<>();
			for (Obs obs : encounter.getObs()) {
				Observation observation = observationService.get(obs.getUuid());
				Task.ParameterComponent input = new Task.ParameterComponent();
				input.setType(observation.getCode());
				input.setValue(observation.getValue());
				taskInputs.add(input);
			}
		}
				
		// Create Task Resource for given Order
		Task newTask = createTask(basedOnRefs, forReference, ownerRef, encounterRef,locationRef, requesterRef, taskInputs);
		newTask.setLocation(locationRef);
		newTask.setRequester(requesterRef);

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
