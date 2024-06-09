package org.openmrs.module.labonfhir.api;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.hl7.fhir.r4.model.Task;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;
import org.mockito.Mock;
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
import org.openmrs.module.labonfhir.api.fhir.OrderCreationException;

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

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }
	@Before
	public void setUp() throws Exception {

	}


	@Test
	public void createOrder_shouldCreateTask() throws OrderCreationException {
		// Create a mock Order object
		Order order = new Order();
		order.setUuid("orderUuid");
		
		// Create a mock Encounter object
		Encounter encounter = new Encounter();
		encounter.setUuid("encounterUuid");
		encounter.setLocation(new Location());
		encounter.getLocation().setUuid("locationUuid");
		encounter.setPatient(new Patient());
		encounter.getPatient().setUuid("patientUuid");
		
		// Create a mock Obs object
		Obs obs = new Obs();
		obs.setUuid("obsUuid");
		encounter.getObs().add(obs);
		
		// Mock the necessary dependencies
		when(orderService.getOrderByUuid("orderUuid")).thenReturn(order);
		when(encounterService.getEncounterByUuid("encounterUuid")).thenReturn(encounter);
		when(locationService.getLocationByUuid("locationUuid")).thenReturn(new Location());
		when(patientService.getPatientByUuid("patientUuid")).thenReturn(new Patient());
		when(obsService.getObsByUuid("obsUuid")).thenReturn(new Obs());
		
		// Call the createOrder method
		LabOrderHandler labOrderHandler = new LabOrderHandler();
		Task task = labOrderHandler.createOrder(order);
		
		// Assert that the task is not null
		assertNotNull(task);
	}
}