package org.openmrs.module.labonfhir.api.event;

import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import java.util.HashSet;
import java.util.List;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.TokenAndListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.Order;
import org.openmrs.TestOrder;
import org.openmrs.api.APIException;
import org.openmrs.api.OrderService;
import org.openmrs.api.context.Daemon;
import org.openmrs.event.EventListener;
import org.openmrs.module.DaemonToken;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.OpenElisFhirOrderHandler;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component("labOrderListener")
public class OrderCreationListener implements EventListener {
	
	private static final Logger log = LoggerFactory.getLogger(OrderCreationListener.class);
	
	public DaemonToken getDaemonToken() {
		return daemonToken;
	}
	
	public void setDaemonToken(DaemonToken daemonToken) {
		this.daemonToken = daemonToken;
	}
	
	private DaemonToken daemonToken;
	
	@Autowired
	private IGenericClient client;
	
	@Autowired
	private LabOnFhirConfig config;
	
	@Autowired
	private OrderService orderService;
	
	@Autowired
	private OpenElisFhirOrderHandler handler;
	
	@Autowired
	private FhirTaskService fhirTaskService;
	
	@Autowired
	@Qualifier("fhirR4")
	private FhirContext ctx;
	
	@Override
	public void onMessage(Message message) {
		log.trace("Received message {}", message);
		
		Daemon.runInDaemonThread(() -> {
			try {
				processMessage(message);
			}
			catch (Exception e) {
				log.error("Failed to update the user's last viewed patients property", e);
			}
		}, daemonToken);
	}
	
	public void processMessage(Message message) {
		if (message instanceof MapMessage) {
			MapMessage mapMessage = (MapMessage) message;

			String uuid;
			try {
				uuid = mapMessage.getString("uuid");
				log.debug("Handling order {}", uuid);
			} catch (JMSException e) {
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
			} catch (APIException e) {
				log.error("Exception caught while trying to load order {}", uuid, e);
				return;
			}

			// this is written this way so we can solve whether we can handle this order
			// in one pass through the Obs

			log.trace("Found order(s) for order {}", order);
			try {
				if (order instanceof TestOrder) {
					Task task = handler.createOrder(order);
					if (task != null) {
						if (config.getActivateFhirPush()) {
							Bundle labBundle = createLabBundle(task);
							client.transaction().withBundle(labBundle).execute();
							log.debug(ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(labBundle));
						}
					}
				}
			} catch (OrderCreationException e) {
				log.error("An exception occurred while trying to create the order for order {}", order, e);
			}
		}
	}
	
	private Bundle createLabBundle(Task task) {
		TokenAndListParam uuid = new TokenAndListParam().addAnd(new TokenParam(task.getIdElement().getIdPart()));
		HashSet<Include> includes = new HashSet<>();
		includes.add(new Include("Task:patient"));
		includes.add(new Include("Task:owner"));
		includes.add(new Include("Task:encounter"));
		includes.add(new Include("Task:based-on"));
		
		IBundleProvider labBundle = fhirTaskService.searchForTasks(null, null, null, uuid, null, null, includes);
		labBundle.getAllResources();
		
		Bundle transactionBundle = new Bundle();
		transactionBundle.setType(Bundle.BundleType.TRANSACTION);
		List<IBaseResource> labResources = labBundle.getAllResources();
		for (IBaseResource r : labResources) {
			Resource resource = (Resource) r;
			Bundle.BundleEntryComponent component = transactionBundle.addEntry();
			component.setResource(resource);
			component.getRequest().setUrl(resource.fhirType() + "/" + resource.getIdElement().getIdPart())
			        .setMethod(Bundle.HTTPVerb.PUT);
			
		}
		return transactionBundle;
	}
}
