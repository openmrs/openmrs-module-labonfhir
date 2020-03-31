package org.openmrs.module.labonfhir.api.scheduler;

import javax.inject.Inject;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.param.ReferenceParam;
import lombok.AccessLevel;
import lombok.Setter;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.FhirReference;
import org.openmrs.module.fhir2.FhirTask;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.dao.FhirTaskDao;
import org.openmrs.module.fhir2.api.translators.TaskTranslator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.scheduler.tasks.AbstractTask;
import javax.inject.Inject;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FetchTaskUpdates extends AbstractTask {
	@Inject
	private ISantePlusLabOnFHIRConfig config;

	@Inject
	private FhirTaskDao taskDao;

	@Inject
	private TaskTranslator taskTranslator;

	@Override
	public void execute() {
		if (!config.isOpenElisEnabled()) {
			return;
		}

//		IGenericClient client = clientFactory.newGenericClient(config.getOpenElisUrl());
//		Bundle requestBundle = new Bundle();
//
//		// Query OpenELIS for tasks that match the set of UUIDS
//		// Create a bundle request component for each task you request
//		Collection<String> taskUuids = getOpenelisTaskUuids();
//		if(taskUuids != null && !taskUuids.isEmpty()) {
//			for(String uuid : taskUuids) {
//				requestBundle.addEntry().setRequest(new Bundle.BundleEntryRequestComponent().setMethod(Bundle.HTTPVerb.GET).setUrl(config.getOpenElisUrl()+"/Task/"+uuid));
//			}
//		}
//
//		Bundle outcomes = client.transaction().withBundle(requestBundle).encodedJson().execute();
//
//		for (Iterator resources = outcomes.getEntry().iterator(); resources.hasNext(); ) {
//
//			// Update task status and output
//			taskDao.saveTask(taskTranslator.toOpenmrsType((Task) resources.next()));
//
//		}
	}

	private Collection<String> getOpenelisTaskUuids() {
		ReferenceParam ownerRef = new ReferenceParam().setValue(FhirConstants.PRACTITIONER + "/" + config.getOpenElisUserUuid());
		Collection<FhirTask> openelisTasks = taskDao.searchForTasks(null, ownerRef, null, null);
		if(!openelisTasks.isEmpty()){
			return openelisTasks.stream().map(FhirTask::getUuid).collect(Collectors.toList());
		} else {
			return Collections.EMPTY_LIST;
		}
	}


}
