package org.openmrs.module.labonfhir.api.event;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;

import org.openmrs.Encounter;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.event.EventListener;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.messaging.LabOrderQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("labEncounterListener")
public class EncounterCreationListener implements EventListener {

	private static final Logger log = LoggerFactory.getLogger(EncounterCreationListener.class);

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	private EncounterService encounterService;

	@Autowired
	private LabOrderQueue labOrderQueue;

	@Override
	public void onMessage(Message message) {
		log.trace("Received message {}", message);

		if (message.getClass().isAssignableFrom(MapMessage.class)) {
			MapMessage mapMessage = (MapMessage) message;

			String uuid;
			try {
				uuid = mapMessage.getString("uuid");
				log.debug("Handling encounter {}", uuid);
			}
			catch (JMSException e) {
				log.error("Exception caught while trying to get encounter uuid for event", e);
				return;
			}

			Encounter encounter;
			try {
				encounter = encounterService.getEncounterByUuid(uuid);
				log.trace("Fetched encounter {}", encounter);
			}
			catch (APIException e) {
				log.error("Exception caught while trying to load encounter {}", uuid, e);
				return;
			}

			if (encounter.getObs().stream().anyMatch(config.isTestOrder())) {
				log.trace("Found order(s) for encounter {}", encounter);
				labOrderQueue.handleOrderEncounter(encounter);
			} else {
				log.trace("No orders found for encounter {}", encounter);
			}
		}
	}
}
