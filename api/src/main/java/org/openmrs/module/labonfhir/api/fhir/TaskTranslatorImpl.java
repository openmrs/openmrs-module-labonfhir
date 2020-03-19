package org.openmrs.module.labonfhir.api.fhir;

import org.apache.commons.lang.StringUtils;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.module.fhir2.api.FhirServiceRequestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class TaskTranslatorImpl extends org.openmrs.module.fhir2.api.translators.impl.TaskTranslatorImpl {

//	@Autowired
//	@Qualifier("labFhirServiceRequestService")
//	private FhirServiceRequestService serviceRequestService;
//
//	@Override
//	public Task toFhirResource(org.openmrs.module.fhir2.FhirTask openmrsTask) {
//		Task task  = super.toFhirResource(openmrsTask);
//
//		if (StringUtils.isNotBlank(openmrsTask.getBasedOn())) {
//			Reference reference = new Reference(openmrsTask.getBasedOn());
//			task.addBasedOn(reference);
//
//			if (!reference.isEmpty() && reference.getType().equals("ServiceRequest")) {
//				ServiceRequest serviceRequest = serviceRequestService.getServiceRequestByUuid(reference.getId());
//				task.setRequester(serviceRequest.getRequester());
//				task.setEncounter(serviceRequest.getEncounter());
//				task.setFor(serviceRequest.getSubject());
//			}
//		}
//
//		return task;
//	}
//
//	@Override
//	public org.openmrs.module.fhir2.FhirTask toOpenmrsType(Task fhirTask) {
//		org.openmrs.module.fhir2.FhirTask task = super.toOpenmrsType(fhirTask);
//
//		if (fhirTask.hasBasedOn()) {
//			task.setBasedOn(fhirTask.getBasedOnFirstRep().getReference());
//		}
//
//		return task;
//	}
//
//	@Override
//	public org.openmrs.module.fhir2.FhirTask toOpenmrsType(org.openmrs.module.fhir2.FhirTask openmrsTask, Task fhirTask) {
//		org.openmrs.module.fhir2.FhirTask task = super.toOpenmrsType(openmrsTask, fhirTask);
//
//		if (fhirTask.hasBasedOn()) {
//			task.setBasedOn(fhirTask.getBasedOnFirstRep().getReference());
//		}
//
//		return task;
//	}
}
