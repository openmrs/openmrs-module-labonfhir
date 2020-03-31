package org.openmrs.module.labonfhir;

import java.util.function.Predicate;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Obs;
import org.openmrs.api.AdministrationService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ISantePlusLabOnFHIRConfig {

	public static final String GP_TEST_ORDER_CONCEPT_UUID = "labonfihr.testsOrderedConceptUuid";

	public static final String GP_ORDER_DESTINATION_CONCEPT_UUID = "labonfhir.orderDestinationConceptUuid";

	public static final String GP_OPENELIS_URL = "https://testapi.openelisci.org:8444/hapi-fhir-jpaserver/";

	public static final String OPENELIS_USER_UUID = "3f7d1c6b-2781-4707-847c-03d4cb579470";

	@Autowired
	@Qualifier("adminService")
	AdministrationService administrationService;

	public String getOpenElisUrl() {
		return administrationService.getGlobalProperty(GP_OPENELIS_URL);
	}

	public String getTestOrderConceptUuid() {
		return administrationService.getGlobalProperty(GP_TEST_ORDER_CONCEPT_UUID);
	}

	public String getOrderDestinationConceptUuid() {
		return administrationService.getGlobalProperty(GP_ORDER_DESTINATION_CONCEPT_UUID);
	}

	public String getOpenElisUserUuid() {
		return OPENELIS_USER_UUID;
	}

	public Predicate<Obs> isTestOrder() {
		final String testOrderConceptUuid = getTestOrderConceptUuid();
		return o -> testOrderConceptUuid.equals(o.getConcept().getUuid());
	}

	public boolean isOpenElisEnabled() {
		return StringUtils.isNotBlank(getOpenElisUrl());
	}
}
