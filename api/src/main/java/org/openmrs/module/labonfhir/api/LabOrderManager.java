package org.openmrs.module.labonfhir.api;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.GlobalProperty;
import org.openmrs.Order;
import org.openmrs.api.GlobalPropertyListener;
import org.openmrs.event.Event;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.event.EncounterCreationListener;
import org.openmrs.module.labonfhir.api.event.OrderCreationListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LabOrderManager implements GlobalPropertyListener {

	private static final Logger log = LoggerFactory.getLogger(LabOrderManager.class);

	public void setDaemonToken(DaemonToken daemonToken) {
		this.daemonToken = daemonToken;
	}

	private DaemonToken daemonToken;

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	private EncounterCreationListener encounterListener;

	@Autowired
	private OrderCreationListener orderListener;

	private final AtomicBoolean isRunning = new AtomicBoolean(false);

	@Override
	public boolean supportsPropertyName(String propertyName) {
		return LabOnFhirConfig.GP_LIS_URL.equals(propertyName);
	}

	@Override
	public void globalPropertyChanged(GlobalProperty newValue) {
		 log.trace("Notified of change to property {}", LabOnFhirConfig.GP_LIS_URL);

		if (StringUtils.isNotBlank((String) newValue.getValue())) {
			enableLisConnector();
		} else {
			disableLisConnector();
		}
	}

	@Override
	public void globalPropertyDeleted(String propertyName) {
		disableLisConnector();
	}

	public void enableLisConnector() {
		log.info("Enabling LIS FHIR Connector for "+config.getLabUpdateTriggerObject());
		if(config.getLabUpdateTriggerObject().equals("Encounter")) {
			encounterListener.setDaemonToken(daemonToken);

			if (!isRunning.get()) {
				Event.subscribe(Encounter.class, Event.Action.CREATED.toString(), encounterListener);
			}
		} else if(config.getLabUpdateTriggerObject().equals("Order")) {
			orderListener.setDaemonToken(daemonToken);

			if (!isRunning.get()) {
				Event.subscribe(Order.class, Event.Action.CREATED.toString(), orderListener);
			}
		} else {
			log.error("Could not enable LIS connection, invalid trigger object: " + config.getLabUpdateTriggerObject());
			return;
		}

		isRunning.set(true);
	}

	public void disableLisConnector() {
		log.info("Disabling LIS FHIR Connector");
		if (isRunning.get() && config.getLabUpdateTriggerObject().equals("Order")) {
			Event.unsubscribe(Order.class, Event.Action.CREATED, orderListener);
		} else if (isRunning.get() && config.getLabUpdateTriggerObject().equals("Encounter")) {
			Event.unsubscribe(Encounter.class, Event.Action.CREATED, encounterListener);
		}
		isRunning.set(false);
	}
}
