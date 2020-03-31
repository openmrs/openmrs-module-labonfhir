package org.openmrs.module.labonfhir.api;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.api.GlobalPropertyListener;
import org.openmrs.event.Event;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.event.EncounterCreationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Inject;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenElisManager implements GlobalPropertyListener {

	private static final Logger log = LoggerFactory.getLogger(OpenElisManager.class);

	@Autowired
	private EncounterCreationListener encounterListener;

	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	@Override
	public boolean supportsPropertyName(String propertyName) {
		return ISantePlusLabOnFHIRConfig.GP_OPENELIS_URL.equals(propertyName);
	}

	@Override
	public void globalPropertyChanged(GlobalProperty newValue) {
		log.trace("Notified of change to property {}", ISantePlusLabOnFHIRConfig.GP_OPENELIS_URL);

		if (StringUtils.isNotBlank((String) newValue.getValue())) {
			enableOpenElisConnector();
		} else {
			disableOpenElisConnector();
		}
	}

	@Override
	public void globalPropertyDeleted(String propertyName) {
		disableOpenElisConnector();
	}

	public void enableOpenElisConnector() {
		log.info("Enabling OpenElis FHIR Connector");
		if (!isRunning.get()) {
			Event.subscribe(Encounter.class, Event.Action.CREATED.toString(), encounterListener);
		}
		isRunning.set(true);
	}

	public void disableOpenElisConnector() {
		log.info("Disabling OpenElis FHIR Connector");
		if (isRunning.get()) {
			Event.unsubscribe(Encounter.class, Event.Action.CREATED, encounterListener);
		}
		isRunning.set(false);
	}
}
