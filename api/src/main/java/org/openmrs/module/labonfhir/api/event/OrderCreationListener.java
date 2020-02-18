package org.openmrs.module.labonfhir.api.event;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Encounter;
import org.openmrs.api.APIException;
import org.openmrs.api.EncounterService;
import org.openmrs.module.labonfhir.api.OpenElisFhirOrderSender;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("labOrderCreationListener")
public class OrderCreationListener implements MessageListener {

	private static final Logger log = LoggerFactory.getLogger(OrderCreationListener.class);

	@Autowired
	private EncounterService encounterService;

	@Autowired
	private OpenElisFhirOrderSender orderSender;

	@Override
	public void onMessage(Message message) {
		log.trace("Received message {}", message);

		String encounterUuid;
		try {
			 encounterUuid = message.getStringProperty("uuid");
		}
		catch (JMSException e) {
			log.error("Error while retrieving encounter uuid", e);
			acknowledge(message);
			return;
		}

		if (StringUtils.isNotBlank(encounterUuid)) {
			Encounter encounter;
			try {
				encounter = encounterService.getEncounterByUuid(encounterUuid);
			}
			catch (APIException e) {
				log.error("Error while retrieving encounter {}", encounterUuid, e);
				acknowledge(message);
				return;
			}

			if (encounter == null) {
				log.warn("Cannot find encounter {}", encounterUuid);
				acknowledge(message);
				return;
			}

			try {
				orderSender.createOrder(encounter);
			}
			catch (OrderCreationException e) {
				log.error("Error while trying to create order for encounter {}", encounter, e);
				return;
			}

			acknowledge(message);
		} else {
			log.trace("Received blank encounter uuid for message {}", message);
		}
	}

	private void acknowledge(Message message) {
		try {
			message.acknowledge();
		} catch (JMSException e) {
			log.error("Error while acknowledging message {}", message, e);
		}
	}
}
