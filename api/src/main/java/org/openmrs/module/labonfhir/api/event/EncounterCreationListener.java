package org.openmrs.module.labonfhir.api.event;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.event.EventListener;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.OpenElisFhirOrderHandler;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
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
	private OpenElisFhirOrderHandler handler;

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

			if (uuid == null || StringUtils.isBlank(uuid)) {
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

			// this is written this way so we can solve whether we can handle this encounter in one pass through the Obs
			boolean openElisOrder = false;
			boolean testOrder = false;
			String orderDestinationUuid = config.getOrderDestinationConceptUuid();
			String testOrderConceptUuid = config.getTestOrderConceptUuid();
			for (Obs obs : encounter.getObs()) {
				if (openElisOrder && testOrder) {
					break;
				}

				String obsConceptUuid = obs.getConcept().getUuid();
				if (orderDestinationUuid.equals(obsConceptUuid)) {
					if (!openElisOrder) {
						if ("OpenElis".equalsIgnoreCase(obs.getValueText())) {
							openElisOrder = true;
						}
					}
				} else if (testOrderConceptUuid.equals(obs.getConcept().getUuid())) {
					if (!testOrder) {
						testOrder = true;
					}
				}
			}

			if (openElisOrder && testOrder) {
				log.trace("Found order(s) for encounter {}", encounter);
				try {
					handler.createOrder(encounter);
				}
				catch (OrderCreationException e) {
					log.error("An exception occurred while trying to create the order for encounter {}", encounter, e);
				}
			} else {
				log.trace("No orders found for encounter {}", encounter);
			}
		}
	}
}
