package org.openmrs.module.labonfhir.api.event;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Encounter;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.module.labonfhir.api.LabOrderHandler;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("labEncounterListener")
public class EncounterCreationListener extends LabCreationListener {
	private static final Logger log = LoggerFactory.getLogger(OrderCreationListener.class);
	@Autowired
	private EncounterService encounterService;

	@Autowired
	private LabOrderHandler handler;

	public void processMessage(Message message) {
		if (message instanceof MapMessage) {
			MapMessage mapMessage = (MapMessage) message;

			String uuid;
			try {
				uuid = mapMessage.getString("uuid");
				log.debug("Handling encounter {}", uuid);
			} catch (JMSException e) {
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
			} catch (APIException e) {
				log.error("Exception caught while trying to load encounter {}", uuid, e);
				return;
			}

			// this is written this way so we can solve whether we can handle this encounter
			// in one pass through the Obs
			boolean lisOrder = true;
			boolean testOrder = true;

			if (lisOrder && testOrder) {
				log.trace("Found order(s) for encounter {}", encounter);
				try {
					Task task = handler.createOrder(encounter);
					sendTask(task);
				} catch (OrderCreationException e) {
					log.error("An exception occurred while trying to create the order for encounter {}", encounter, e);
				}
			} else {
				log.trace("No orders found for encounter {}", encounter);
			}
		}
	}
}
