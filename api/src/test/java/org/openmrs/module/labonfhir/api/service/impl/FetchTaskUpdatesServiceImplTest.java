package org.openmrs.module.labonfhir.api.service.impl;

import static org.mockito.Mockito.when;

import java.util.Collections;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.springframework.stereotype.Component;

@RunWith(MockitoJUnitRunner.class)
@Component
public class FetchTaskUpdatesServiceImplTest {
	
	private static final String OPENMRS_TASK_UUID = "44fdc8ad-fe4d-499b-93a8-8a991c1d477e";
	
	private static final String OPENELIS_TASK_UUID = "55fdc8ad-fe4d-499b-93a8-8a991c1d4788";
	
	private FetchTaskUpdatesServiceImpl fetchTaskUpdatesService;
	
	@Mock
	private Bundle taskBundle;
	
	@Mock
	private FhirTaskService taskService;
	
	@Before
	public void setup() {
		fetchTaskUpdatesService = new FetchTaskUpdatesServiceImpl();
		
		fetchTaskUpdatesService.setTaskService(taskService);;
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
		
		openelisTask.setBasedOn(Collections.singletonList(new Reference().setReference("Task/" + OPENMRS_TASK_UUID)));
		
		Bundle.BundleEntryComponent bec = new Bundle.BundleEntryComponent().setResource(openelisTask);
		
		when(taskBundle.getEntry()).thenReturn(Collections.singletonList(bec));
		when(taskService.get(OPENMRS_TASK_UUID)).thenReturn(openelisTask);
		when(taskService.update(Matchers.eq(OPENMRS_TASK_UUID), Matchers.any(Task.class))).thenReturn(updatedOpenmrsTask);
		
		// Collection<Task> result = updateTask.updateTasksInBundle(taskBundle);
		
		// assertThat(result, hasSize(1));
		
	}
	
}
