package org.openmrs.module.labonfhir;

import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;
import org.hl7.fhir.r4.model.Practitioner;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.Provider;
import org.openmrs.User;
import org.openmrs.api.AdministrationService;

import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirPractitionerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ISantePlusLabOnFHIRConfig {
	// https://wiki.openmrs.org/display/docs/Setting+and+Reading+Global+Properties?src=contextnavpagetreemode
	public static final String GP_TEST_ORDER_CONCEPT_UUID = "labonfihr.testsOrderedConceptUuid";

	public static final String GP_ORDER_DESTINATION_CONCEPT_UUID = "labonfhir.orderDestinationConceptUuid";

	public static final String GP_OPENELIS_URL = "labonfhir.openElisUrl";

	public static final String GP_OPENELIS_USER_UUID = "labonfhir.openelisUserUuid";

	private static final String TEMP_DEFAULT_OPENELIS_URL = "https://testapi.openelisci.org:8444/hapi-fhir-jpaserver";

	@Autowired
	@Qualifier("adminService")
	AdministrationService administrationService;

	@Autowired
	FhirPractitionerService practitionerService;

	public String getOpenElisUrl() {
		//return GP_OPENELIS_URL
		String url = administrationService.getGlobalProperty(GP_OPENELIS_URL);

		if(StringUtils.isBlank(url)) {
			url = TEMP_DEFAULT_OPENELIS_URL;
		}

		return url;
	}

	public String getTestOrderConceptUuid() {
		return administrationService.getGlobalProperty(GP_TEST_ORDER_CONCEPT_UUID);
	}

	public String getOrderDestinationConceptUuid() {
		return administrationService.getGlobalProperty(GP_ORDER_DESTINATION_CONCEPT_UUID);
	}

	public String getOpenElisUserUuid() {
		return administrationService.getGlobalProperty(GP_OPENELIS_USER_UUID);
	}

	public Predicate<Obs> isTestOrder() {
		final String testOrderConceptUuid = getTestOrderConceptUuid();
		return o -> testOrderConceptUuid.equals(o.getConcept().getUuid());
	}

	public boolean isOpenElisEnabled() {
		return StringUtils.isNotBlank(getOpenElisUrl());
	}

	public Practitioner getOpenElisPractitioner() {
		return practitionerService.getPractitionerByUuid(getOpenElisUserUuid());
	}
}
