package org.openmrs.module.labonfhir.api;

import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.api.GlobalPropertyListener;
import org.openmrs.event.Event;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.event.EncounterCreationListener;
import org.openmrs.module.labonfhir.api.messaging.LabOrderQueue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenElisManager implements GlobalPropertyListener {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private LabOrderQueue queue;

	@Autowired
	private EncounterCreationListener encounterListener;

	@Override
	public boolean supportsPropertyName(String propertyName) {
		return ISantePlusLabOnFHIRConfig.GP_OPENELIS_URL.equals(propertyName);
	}

	@Override
	public void globalPropertyChanged(GlobalProperty newValue) {
		if (config.isOpenElisEnabled()) {
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
		queue.startup();
		Event.subscribe(Encounter.class, Event.Action.CREATED.toString(), encounterListener);
	}

	public void disableOpenElisConnector() {
		try {
			queue.shutdown();
		} finally {
			Event.unsubscribe(Encounter.class, Event.Action.CREATED, encounterListener);
		}
	}
}
