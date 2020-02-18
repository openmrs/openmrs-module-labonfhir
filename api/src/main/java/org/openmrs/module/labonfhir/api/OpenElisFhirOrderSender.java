package org.openmrs.module.labonfhir.api;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.module.fhir2.api.translators.PatientTranslator;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.openmrs.module.fhir2.api.translators.TaskTranslator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenElisFhirOrderSender {

	@Autowired
	private IRestfulClientFactory clientFactory;

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private PatientTranslator patientTranslator;

	@Autowired
	private ServiceRequestTranslator<Obs> serviceRequestTranslator;

	@Autowired
	private TaskTranslator taskTranslator;

	public void createOrder(Encounter encounter) throws OrderCreationException {
		AtomicReference<Obs> orderObs = new AtomicReference<>();
		encounter.getObs().stream().filter(config.isTestOrder()).findFirst().ifPresent(orderObs::set);

		if (orderObs.get() == null) {
			throw new OrderCreationException("Could not find order for encounter " + encounter);
		}

		ServiceRequest serviceRequest = serviceRequestTranslator.toFhirResource(orderObs.get());
		serviceRequest.setStatus(ServiceRequest.ServiceRequestStatus.ACTIVE);

		Patient patient = patientTranslator.toFhirResource(encounter.getPatient());

		org.openmrs.module.fhir2.Task newTask = new org.openmrs.module.fhir2.Task();
		newTask.setBasedOn("ServiceRequest/" + orderObs.get().getUuid());
		Task task = taskTranslator.toFhirResource(newTask);

		IGenericClient client = clientFactory.newGenericClient(config.getOpenElisUrl());

		Bundle theMessage = new Bundle().setType(Bundle.BundleType.TRANSACTION);
		theMessage.addEntry().setResource(task)
				.setRequest(
						new Bundle.BundleEntryRequestComponent().setUrl("Task").setMethod(Bundle.HTTPVerb.POST));
		theMessage.addEntry().setResource(serviceRequest)
				.setRequest(
						new Bundle.BundleEntryRequestComponent().setUrl("ServiceRequest").setMethod(Bundle.HTTPVerb.POST));
		theMessage.addEntry().setResource(patient)
				.setRequest(
						new Bundle.BundleEntryRequestComponent().setUrl("Patient").setMethod(Bundle.HTTPVerb.POST));

		Bundle outcome = client.transaction().withBundle(theMessage).encodedJson().execute();
		if (outcome == null) {
			throw new OrderCreationException("Error creating order in OpenELIS for encounter " + encounter);
		} else {
			List<OperationOutcome> outcomes = outcome.getEntry().stream().filter(Bundle.BundleEntryComponent::hasResource)
					.map(Bundle.BundleEntryComponent::getResource)
					.filter(r -> r.getClass().isAssignableFrom(OperationOutcome.class)).map(r -> (OperationOutcome) r)
					.filter(this::hasErrorMessage).collect(Collectors.toList());

			if (outcomes.size() > 0) {
				throw new OrderCreationException(
						"Error creating order in OpenELIS for encounter "
								+ encounter
								+ ":\n"
								+ outcomes.stream().map(o -> o.getIssue().stream().map(this::operationOutcomeToMessage)
								.collect(Collectors.joining("\n")))
								.collect(Collectors.joining("\n")));
			}
		}
	}

	private boolean hasErrorMessage(OperationOutcome operationOutcome) {
		return operationOutcome.hasIssue() && operationOutcome.getIssue().stream().anyMatch(c ->
				c.hasSeverity() && c.getSeverity().ordinal() < OperationOutcome.IssueSeverity.WARNING.ordinal());
	}

	private String operationOutcomeToMessage(OperationOutcome.OperationOutcomeIssueComponent component) {
		StringBuilder sb = new StringBuilder();

		if (component.hasSeverity() && component.getSeverity().ordinal() < OperationOutcome.IssueSeverity.INFORMATION.ordinal()) {
			sb.append(component.getSeverity().getDisplay()).append(": ");
		}

		if (component.hasCode()) {
			sb.append(component.getCode()).append(" - ");
		}

		if (component.hasExpression()) {
			sb.append(component.getExpression()).append(" - ");
		}

		if (component.hasDetails()) {
			sb.append(component.getDetails());
		}

		return sb.toString();
	}
}
