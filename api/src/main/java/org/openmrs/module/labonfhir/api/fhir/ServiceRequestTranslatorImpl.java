package org.openmrs.module.labonfhir.api.fhir;

import java.util.Collection;
import java.util.Set;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.EncounterProvider;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.openmrs.module.fhir2.api.translators.impl.AbstractReferenceHandlingTranslator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("labOnFHIRServiceRequestTranslatorImpl")
public class ServiceRequestTranslatorImpl extends AbstractReferenceHandlingTranslator implements ServiceRequestTranslator<Obs> {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private ConceptTranslator conceptTranslator;

	@Autowired
	private FhirTaskService taskService;

	@Override
	public ServiceRequest toFhirResource(Obs obs) {
		if (obs == null || obs.getConcept() == null || !obs.getConcept().getUuid().equals(config.getTestOrderConceptUuid())) {
			return null;
		}

		ServiceRequest serviceRequest = new ServiceRequest();

		serviceRequest.setId(obs.getUuid());
		serviceRequest.setStatus(determineServiceRequestStatus(obs.getUuid()));
		serviceRequest.setCode(conceptTranslator.toFhirResource(obs.getValueCoded()));
		serviceRequest.setIntent(ServiceRequest.ServiceRequestIntent.ORDER);

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

		if (obs.getPerson() != null && Patient.class.isAssignableFrom(obs.getPerson().getClass())) {
			serviceRequest.setSubject(createPatientReference((Patient) obs.getPerson()));
		}

		return serviceRequest;
	}

	protected ServiceRequest.ServiceRequestStatus determineServiceRequestStatus(String orderUuid) {
		Collection<Task> serviceRequestTasks = taskService.getTasksByBasedOn(ServiceRequest.class, orderUuid);

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
