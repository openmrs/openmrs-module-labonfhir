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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.BaseModuleActivator;
import org.openmrs.module.labonfhir.api.OpenElisManager;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class ISantePlusLabOnFHIRActivator extends BaseModuleActivator implements ApplicationContextAware {
	
	private Log log = LogFactory.getLog(this.getClass());

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	@Qualifier("openElisManager")
	private OpenElisManager gpListener;

	@Override
	public void started() {
		// subscribe to encounter creation events
		if (config.isOpenElisEnabled()) {
			gpListener.enableOpenElisConnector();
		}

		log.info("Started iSantePlus Lab on FHIR Module");
	}

	@Override
	public void stopped() {
		gpListener.disableOpenElisConnector();

		log.info("Shutdown iSantePlus Lab on FHIR Module");
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
	}
}
