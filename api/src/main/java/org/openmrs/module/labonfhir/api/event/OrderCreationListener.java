package org.openmrs.module.labonfhir.api.event;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.module.labonfhir.api.LabOrderHandler;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("labOrderListener")
public class OrderCreationListener extends LabCreationListener {
	
	private static final Logger log = LoggerFactory.getLogger(OrderCreationListener.class);
	
	@Autowired
	private OrderService orderService;
	
	@Autowired
	private LabOrderHandler handler;
	
	@Override
	public void processMessage(Message message) {
		if (message instanceof MapMessage) {
			MapMessage mapMessage = (MapMessage) message;
			
			String uuid;
			try {
				uuid = mapMessage.getString("uuid");
				log.debug("Handling order {}", uuid);
			}
			catch (JMSException e) {
				log.error("Exception caught while trying to get order uuid for event", e);
				return;
			}
			
			if (uuid == null || StringUtils.isBlank(uuid)) {
				return;
			}
			
			Order order;
			try {
				order = orderService.getOrderByUuid(uuid);
				log.trace("Fetched order {}", order);
			}
			catch (APIException e) {
				log.error("Exception caught while trying to load order {}", uuid, e);
				return;
			}
			
			// this is written this way so we can solve whether we can handle this order
			// in one pass through the Obs
			
			log.trace("Found order(s) for order {}", order);
			try {
				if (order instanceof TestOrder) {
					Task task = handler.createOrder(order);
					sendTask(task);
				}
			}
			catch (OrderCreationException e) {
				log.error("An exception occurred while trying to create the order for order {}", order, e);
			}
		}
	}
}
