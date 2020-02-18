package org.openmrs.module.labonfhir.api.messaging;

import javax.jms.ConnectionFactory;
import javax.jms.Message;
import javax.jms.Queue;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQQueue;
import org.openmrs.Encounter;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.CachingConnectionFactory;
import org.springframework.jms.connection.SingleConnectionFactory;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

@Component
public class LabOrderQueue {

	private static final Logger log = LoggerFactory.getLogger(LabOrderQueue.class);

	private JmsTemplate jmsTemplate = null;
	private SingleConnectionFactory connectionFactory = null;
	private Queue queue = null;

	public void startup() {
		if (connectionFactory == null) {
			log.info("creating connection factory");
			String dataDirectory = new File(OpenmrsUtil.getApplicationDataDirectory(), "activemq-data").getAbsolutePath();
			try {
				dataDirectory = URLEncoder.encode(dataDirectory, "UTF-8");
			}
			catch (UnsupportedEncodingException e) {
				throw new RuntimeException("Failed to encode URI", e);
			}

			String brokerURL = "vm://localhost?broker.persistent=true&broker.useJmx=false&broker.dataDirectory="
					+ dataDirectory;
			ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory(brokerURL);
			connectionFactory = new CachingConnectionFactory(cf);
		}

		if (queue == null) {
			queue = new ActiveMQQueue("labOrderQueue");
		}

		if (jmsTemplate == null) {
			log.debug("creating JmsTemplate");
			jmsTemplate = new JmsTemplate(connectionFactory);
		} else {
			log.trace("messageListener already defined");
		}
	}

	public void handleOrderEncounter(Encounter encounter) {
		jmsTemplate.send(queue, s -> {
			Message message = s.createMessage();
			message.setStringProperty("uuid", encounter.getUuid());
			return message;
		});
	}

	public void shutdown() {
		log.debug("Shutting down JMS connection...");

		if (connectionFactory != null) {
			connectionFactory.destroy();
		}
	}
}
