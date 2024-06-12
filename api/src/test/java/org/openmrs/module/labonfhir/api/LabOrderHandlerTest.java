package org.openmrs.module.labonfhir.api;

import org.hl7.fhir.r4.model.Task;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.Patient;
import org.openmrs.api.EncounterService;
import org.openmrs.api.LocationService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.module.fhir2.api.FhirObservationService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class LabOrderHandlerTest {
	@Mock
	private OrderService orderService;
	@Mock
	private EncounterService encounterService;
	@Mock
	private LocationService locationService;
	@Mock
	private PatientService patientService;
	@Mock
	private ObsService obsService;
	@Mock
	private LabOnFhirConfig labOnFhirConfig;
	@Mock
	private FhirObservationService fhirObservationService;
	@Mock
	private FhirTaskService fhirTaskService;

	@Before
	public void setUp() throws Exception {
		MockitoAnnotations.initMocks(this);
	}

	@Test
	public void createOrder_shouldCreateTask() throws OrderCreationException {
		// Create a mock Patient object
		Patient patient = new Patient();
		patient.setUuid("patientUuid");

		// Create a mock Encounter object
		Encounter encounter = new Encounter();
		encounter.setUuid("encounterUuid");
		encounter.setLocation(new Location());
		encounter.getLocation().setUuid("locationUuid");
		encounter.setPatient(patient);

		// Create a mock Order object
		Order order = new Order();
		order.setUuid("orderUuid");
		order.setPatient(patient);

		// Create a mock Obs object
		Obs obs = new Obs();
		obs.setUuid("obsUuid");
		obs.setPerson(patient);
		obs.setConcept(new Concept());
		obs.getConcept().setUuid("conceptUuid");

		obs.setEncounter(encounter);
		encounter.getObs().add(obs);
		encounter.addOrder(order);

		// Mock the necessary dependencies
		when(orderService.getOrderByUuid("orderUuid")).thenReturn(order);
		when(encounterService.getEncounterByUuid("encounterUuid")).thenReturn(encounter);
		when(locationService.getLocationByUuid("locationUuid")).thenReturn(new Location());
		when(patientService.getPatientByUuid("patientUuid")).thenReturn(new Patient());
		when(obsService.getObsByUuid("obsUuid")).thenReturn(new Obs());
		when(fhirTaskService.create(any())).then((Answer<Task>) invocation -> (Task) invocation.getArguments()[0]);

		// Call the createOrder method
		LabOrderHandler labOrderHandler = new LabOrderHandler();
		labOrderHandler.setConfig(labOnFhirConfig);
		labOrderHandler.setObservationService(fhirObservationService);
		labOrderHandler.setTaskService(fhirTaskService);

		Task task = labOrderHandler.createOrder(order);

		// Assert that the task is not null
		assertThat(task, notNullValue());
		assertThat(task.hasRequester(), is(true));
		assertThat(task.getRequester().getReferenceElement().getIdPart(), is("locationUuid"));
	}
}
