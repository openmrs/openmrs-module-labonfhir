/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.labonfhir;

import lombok.SneakyThrows;

import java.util.Optional;

import org.openmrs.PatientIdentifierType;
import org.openmrs.api.PatientService;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.DaemonTokenAware;
import org.openmrs.module.fhir2.api.FhirPatientIdentifierSystemService;
import org.openmrs.module.fhir2.model.FhirPatientIdentifierSystem;
import org.openmrs.module.labonfhir.api.LabOrderManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class LabOnFhirActivator extends BaseModuleActivator implements ApplicationContextAware, DaemonTokenAware {
	
	private static final Logger log = LoggerFactory.getLogger(LabOnFhirActivator.class);
	
	private static ApplicationContext applicationContext;
	
	private static DaemonToken daemonToken;
	
	@Autowired
	private LabOnFhirConfig config;
	
	@Autowired
	private LabOrderManager lisManager;
	
	@Autowired
	PatientService patientService;
	
	@Autowired
	FhirPatientIdentifierSystemService fhirPatientIdentifierSystemService;
	
	@SneakyThrows
	@Override
	public void started() {
		applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		
		lisManager.setDaemonToken(daemonToken);
		
		// subscribe to encounter creation events
		if (config.isLisEnabled()) {
			lisManager.enableLisConnector();
		}
		createFhirPatientIdentierSystem();
		
		log.info("Lab on FHIR Module Started!");
		
	}
	
	@Override
	public void stopped() {
		if (lisManager != null) {
			lisManager.disableLisConnector();
		}
		
		log.info("Lab on FHIR Module Shut Down!");
	}
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
	
	@Override
	public void setDaemonToken(DaemonToken token) {
		this.daemonToken = token;
	}
	
	private void createFhirPatientIdentierSystem() {
		PatientIdentifierType pidType = patientService
				.getPatientIdentifierTypeByUuid(config.getPatientIdentifierUuid().trim());

		Optional<FhirPatientIdentifierSystem> existingIdSystem = fhirPatientIdentifierSystemService
				.getFhirPatientIdentifierSystem(pidType);
		if (existingIdSystem.isPresent()) {
			existingIdSystem.get().setPatientIdentifierType(pidType);
			existingIdSystem.get().setUrl(config.getLisIdentifierSystemUrl().trim());
			fhirPatientIdentifierSystemService.saveFhirPatientIdentifierSystem(existingIdSystem.get());
		} else {
			FhirPatientIdentifierSystem idSystem = new FhirPatientIdentifierSystem();
			idSystem.setName("OpenLIS ID System");
			idSystem.setPatientIdentifierType(pidType);
			idSystem.setUrl(config.getLisIdentifierSystemUrl().trim());
			fhirPatientIdentifierSystemService.saveFhirPatientIdentifierSystem(idSystem);
		}
	}
}
