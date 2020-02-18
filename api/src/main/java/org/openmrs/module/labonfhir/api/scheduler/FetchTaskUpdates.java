package org.openmrs.module.labonfhir.api.scheduler;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FetchTaskUpdates extends AbstractTask {

	@Autowired
	private IRestfulClientFactory clientFactory;

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Override
	public void execute() {
		if (!config.isOpenElisEnabled()) {
			return;
		}

		IGenericClient client = clientFactory.newGenericClient(config.getOpenElisUrl());
		Bundle requestBundle = new Bundle();
		requestBundle.addEntry().setRequest(new Bundle.BundleEntryRequestComponent().setMethod(Bundle.HTTPVerb.GET).setUrl("Task/9999999999999"));

		Bundle outcomes = client.transaction().withBundle(requestBundle).encodedJson().execute();
	}
}
