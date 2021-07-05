package org.openmrs.module.labonfhir.api.fhir;

import java.util.Collection;
import java.util.Set;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.openmrs.module.fhir2.api.translators.impl.AbstractReferenceHandlingTranslator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component("labOnFHIRServiceRequestTranslatorImpl")
public class ServiceRequestTranslatorImpl extends AbstractReferenceHandlingTranslator implements ServiceRequestTranslator<Obs> {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private ConceptTranslator conceptTranslator;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	private ConceptService conceptService;

	@Override
	public ServiceRequest toFhirResource(Obs obs) {
		notNull(obs, "The Obs object should not be null");
		
		notNull(obs, "The Obs object needs to have a non-null Concept");

		// TODO Handle wrong Obs concept better
		if(!obs.getConcept().getUuid().equals(config.getTestOrderConceptUuid()))
			return null;

		ServiceRequest serviceRequest = new ServiceRequest();

		Collection<Task> serviceRequestTasks = taskService.getTasksByBasedOn(ServiceRequest.class, serviceRequest.getId());

		serviceRequest.setId(obs.getUuid());

		serviceRequest.setStatus(determineServiceRequestStatus(obs.getUuid(), serviceRequestTasks));

		serviceRequest.setCode(conceptTranslator.toFhirResource(obs.getValueCoded()));

		serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

		if (obs.getPerson() != null && obs.getPerson().getIsPatient() && false) {
			// TODO: Figure out why I can't cast this to Patient
			serviceRequest.setSubject(createPatientReference((Patient) obs.getPerson()));
		} else if(!serviceRequestTasks.isEmpty()) {
			// fall back on Task
			serviceRequest.setSubject(serviceRequestTasks.iterator().next().getFor());
		}

		if (obs.getEncounter() != null) {
			Encounter encounter = obs.getEncounter();
			serviceRequest.setEncounter(createEncounterReference(encounter));

			Set<EncounterProvider> encounterProviders = encounter.getEncounterProviders();
			if (encounterProviders != null && !encounterProviders.isEmpty()) {
				try {
					serviceRequest
							.setRequester(createPractitionerReference(encounterProviders.iterator().next().getProvider()));
				} catch (Exception ignored) {}
			}
		}

		// TODO: Map Performer
		// serviceRequest.setPerformer(Collections.singletonList(determineServiceRequestPerformer(order.getUuid())));
		
		// TODO: Figure out location of start/end dates
		// serviceRequest
		//         .setOccurrence(new Period().setStart(order.getEffectiveStartDate()).setEnd(order.getEffectiveStopDate()));
		
		serviceRequest.getMeta().setLastUpdated(obs.getDateChanged());
		
		// TODO: Determine if we need replaces or basedOn mapping
		// if (order.getPreviousOrder() != null
		//         && (order.getAction() == Order.Action.DISCONTINUE || order.getAction() == Order.Action.REVISE)) {
		// 	serviceRequest.setReplaces((Collections.singletonList(createOrderReference(order.getPreviousOrder())
		// 	        .setIdentifier(orderIdentifierTranslator.toFhirResource(order.getPreviousOrder())))));
		// } else if (order.getPreviousOrder() != null && order.getAction() == Order.Action.RENEW) {
		// 	serviceRequest.setBasedOn(Collections.singletonList(createOrderReference(order.getPreviousOrder())
		// 	        .setIdentifier(orderIdentifierTranslator.toFhirResource(order.getPreviousOrder()))));
		// }
		


		return serviceRequest;
	}

	protected ServiceRequest.ServiceRequestStatus determineServiceRequestStatus(String orderUuid, Collection<Task> serviceRequestTasks) {
		ServiceRequest.ServiceRequestStatus serviceRequestStatus = ServiceRequest.ServiceRequestStatus.UNKNOWN;

		if (serviceRequestTasks.size() != 1) {
			return serviceRequestStatus;
		}

		Task serviceRequestTask = serviceRequestTasks.iterator().next();

		if (serviceRequestTask.getStatus() != null) {
			switch (serviceRequestTask.getStatus()) {
				case ACCEPTED:
				case REQUESTED:
					serviceRequestStatus = ServiceRequest.ServiceRequestStatus.ACTIVE;
					break;
				case REJECTED:
					serviceRequestStatus = ServiceRequest.ServiceRequestStatus.REVOKED;
					break;
				case COMPLETED:
					serviceRequestStatus = ServiceRequest.ServiceRequestStatus.COMPLETED;
					break;
			}
		}
		return serviceRequestStatus;
	}
}
