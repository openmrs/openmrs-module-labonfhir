package org.openmrs.module.labonfhir.api.fhir;

import org.hl7.fhir.r4.model.ServiceRequest;
import org.openmrs.Obs;
import org.openmrs.module.fhir2.api.FhirServiceRequestService;
import org.openmrs.module.fhir2.api.dao.FhirServiceRequestDao;
import org.openmrs.module.fhir2.api.translators.ServiceRequestTranslator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("labFhirServiceRequestService")
public class FhirServiceRequestServiceImpl implements FhirServiceRequestService {

	@Autowired
	private FhirServiceRequestDao<Obs> dao;

	@Autowired
	private ServiceRequestTranslator<Obs> translator;

	@Override
	public ServiceRequest getServiceRequestByUuid(String uuid) {
		return translator.toFhirResource(dao.getServiceRequestByUuid(uuid));
	}
}
