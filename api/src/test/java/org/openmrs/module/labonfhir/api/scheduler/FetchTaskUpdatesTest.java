package org.openmrs.module.labonfhir.api.scheduler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.any;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IRestfulClientFactory;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.test.context.TestContext;

@RunWith(MockitoJUnitRunner.class)
@Component
public class FetchTaskUpdatesTest {

	private static final String OPENMRS_TASK_UUID = "44fdc8ad-fe4d-499b-93a8-8a991c1d477e";

	private static final String OPENELIS_TASK_UUID = "55fdc8ad-fe4d-499b-93a8-8a991c1d4788";


	private FetchTaskUpdates updateTask;

	@Mock
	private Bundle taskBundle;

	@Mock
	private FhirTaskService taskService;

	@Before
	public void setup() {
		updateTask = new FetchTaskUpdates();

		updateTask.setTaskService(taskService);
	}

	@Test
	public void updateTasksInBundle_shouldUpdateTasks() {
		Task openelisTask = new Task();
		Task openmrsTask = new Task();
		Task updatedOpenmrsTask = new Task();

		openmrsTask.setId(OPENMRS_TASK_UUID);
		openmrsTask.setStatus(Task.TaskStatus.REQUESTED);

		updatedOpenmrsTask.setId(OPENMRS_TASK_UUID);
		updatedOpenmrsTask.setStatus(Task.TaskStatus.ACCEPTED);

		openelisTask.setId(OPENELIS_TASK_UUID);
		openmrsTask.setStatus(Task.TaskStatus.ACCEPTED);

		openelisTask.setBasedOn(Collections.singletonList(new Reference().setReference("Task/"+OPENMRS_TASK_UUID)));

		Bundle.BundleEntryComponent bec = new Bundle.BundleEntryComponent().setResource(openelisTask);

		when(taskBundle.getEntry()).thenReturn(Collections.singletonList(bec));
		when(taskService.getTaskByUuid(OPENMRS_TASK_UUID)).thenReturn(openelisTask);
		when(taskService.updateTask(Matchers.eq(OPENMRS_TASK_UUID), Matchers.any(Task.class))).thenReturn(updatedOpenmrsTask);

		Collection<Task> result = updateTask.updateTasksInBundle(taskBundle);

		assertThat(result, hasSize(1));

	}

}
