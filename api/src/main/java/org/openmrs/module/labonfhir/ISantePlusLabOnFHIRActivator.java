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

import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.labonfhir.api.OpenElisManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
@Component
public class ISantePlusLabOnFHIRActivator extends BaseModuleActivator implements ApplicationContextAware {
	private static final Logger log = LoggerFactory.getLogger(ISantePlusLabOnFHIRActivator.class);
	private ApplicationContext applicationContext;

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	@Qualifier("openElisManager")
	private OpenElisManager openElisManager;

	@Override
	public void started() {
//		if (applicationContext != null) {
//			applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
//
//			// subscribe to encounter creation events
//			if (config.isOpenElisEnabled()) {
//				openElisManager.enableOpenElisConnector();
//			}
//		}
		log.info("Started iSantePlus Lab on FHIR Module");
	}
	@Override
	public void stopped() {
//		if (openElisManager != null) {
//			openElisManager.disableOpenElisConnector();
//		}
		log.info("Shutdown iSantePlus Lab on FHIR Module");
	}
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
