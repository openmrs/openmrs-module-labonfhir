package org.openmrs.module.labonfhir.api.scheduler;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.SessionFactory;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Task;
import org.hl7.fhir.r4.model.codesystems.TaskStatus;
import org.openmrs.Order;
import org.openmrs.api.OrderService;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirObservationService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.dao.FhirObservationDao;
import org.openmrs.module.fhir2.api.translators.ObservationReferenceTranslator;
import org.openmrs.module.labonfhir.LabOnFhirConfig;
import org.openmrs.module.labonfhir.api.model.TaskRequest;
import org.openmrs.module.labonfhir.api.service.LabOnFhirService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;

@Component
@Setter(AccessLevel.PACKAGE)
public class FetchTaskRejected extends AbstractTask implements ApplicationContextAware {

	private static Log log = LogFactory.getLog(FetchTaskUpdates.class);

	private static ApplicationContext applicationContext;

	private static String LOINC_SYSTEM = "http://loinc.org";

	@Autowired
	private LabOnFhirConfig config;

	@Autowired
	@Qualifier("labOrderFhirClient")
	private IGenericClient client;

	@Autowired
	private FhirTaskService taskService;

	@Autowired
	FhirObservationDao observationDao;

	@Autowired
	FhirObservationService observationService;

	@Autowired
	OrderService orderService;

	@Autowired
	ObservationReferenceTranslator observationReferenceTranslator;

	@Autowired
	@Qualifier("sessionFactory")
	SessionFactory sessionFactory;

	@Autowired
    private LabOnFhirService labOnFhirService;

	@Override
	public void execute() {
		
		try {
			applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		}
		catch (Exception e) {
			// return;
		}
		
		if (!config.isLisEnabled()) {
			return;
		}
		
		try {
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
			Date newDate = new Date();
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(newDate);
			calendar.add(Calendar.YEAR, -5);
			Date fiveYearsAgo = calendar.getTime();
			
			TaskRequest lastRequest = labOnFhirService.getLastTaskRequest();
			String lastRequstDate = dateFormat.format(fiveYearsAgo);
			if (lastRequest != null) {
				lastRequstDate = dateFormat.format(lastRequest.getRequestDate());
			}
			
			String currentTime = dateFormat.format(newDate);
			DateRangeParam lastUpdated = new DateRangeParam().setLowerBound(lastRequstDate).setUpperBound(currentTime);
			
			// Get List of Tasks that belong to this instance and update them
			Bundle taskBundle = client.search().forResource(Task.class)
			        .where(Task.IDENTIFIER.hasSystemWithAnyCode(FhirConstants.OPENMRS_FHIR_EXT_TASK_IDENTIFIER))
			        .where(Task.STATUS.exactly().code(TaskStatus.REJECTED.toCode())).lastUpdated(lastUpdated)
			        .returnBundle(Bundle.class).execute();
			
			List<Bundle> taskBundles = new ArrayList<>();
			taskBundles.add(taskBundle);
			//Support FHIR Server Pagination
			while (taskBundle.getLink(IBaseBundle.LINK_NEXT) != null) {
				taskBundle = client.loadPage().next(taskBundle).execute();
				taskBundles.add(taskBundle);
			}
			Boolean tasksUpdated = updateTasksInBundle(taskBundles);
			if (tasksUpdated) {
				TaskRequest request = new TaskRequest();
				request.setRequestDate(newDate);
				labOnFhirService.saveOrUpdateTaskRequest(request);
			}
		}
		catch (Exception e) {
			log.error("ERROR executing FetchTaskUpdates : " + e.toString() + getStackTrace(e));
		}
		super.startExecuting();
	}

	@Override
	public void shutdown() {
		log.debug("shutting down FetchTaskUpdates Task");
		this.stopExecuting();
	}

	private Boolean updateTasksInBundle(List<Bundle> taskBundles) {
		Boolean tasksUpdated = false;
		for (Bundle bundle : taskBundles) {
			for (Iterator tasks = bundle.getEntry().iterator(); tasks.hasNext();) {
				String openmrsTaskUuid = null;
				try {
					Task openelisTask = (Task) ((Bundle.BundleEntryComponent) tasks.next()).getResource();
					openmrsTaskUuid = openelisTask.getIdentifierFirstRep().getValue();
					Task openmrsTask = taskService.get(openmrsTaskUuid);
					if (openmrsTask != null) {
						System.out.println("TASK-STAUS :"+ openelisTask.getStatus().toString());
						System.out.println("TASK-STAUS-2 :"+ TaskStatus.REJECTED.toString());
						System.out.println("OPEN-LIS :"+ TaskStatus.REJECTED.toString());

						System.out.println("OPEN-TASK-COMPARE :"+ openelisTask.getStatus().toString().equals(TaskStatus.REJECTED.toString()));
						if(openelisTask.getStatus().toString().equals(TaskStatus.REJECTED.toString()) ){
							openmrsTask.setStatus(openelisTask.getStatus());
							System.out.println("BaseOn" + openmrsTask.getBasedOn());
							System.out.println("START :");
							System.out.println("BASE-SIZE :"+ openelisTask.getBasedOn().size());
                            setOrderStatus(openmrsTask.getBasedOn(), openelisTask.getStatus().toCode());
							System.out.println("END :");
							tasksUpdated = true;
						}
					}
				}
				catch (Exception e) {
					log.error("Could not save task " + openmrsTaskUuid + ":" + e.toString() + getStackTrace(e));
				}
			}
		}
		return tasksUpdated;
	}

	private void setOrderStatus(List<Reference> basedOn, String string) {
		basedOn.forEach(ref -> {
			if (ref.hasReferenceElement()) {
				System.out.println("concidtion: 1");
				IIdType referenceElement = ref.getReferenceElement();
				if ("ServiceRequest".equals(referenceElement.getResourceType())) {
					System.out.println("concidtion: 2");
					String serviceRequestUuid = referenceElement.getIdPart();
					try {
						
						Order order = orderService.getOrderByUuid(serviceRequestUuid);
						if (order != null) {
							System.out.println("concidtion: 3");
							String commentText = "Update Order with Accesion Number From SIGDEP";
							System.out.println("update order");
							String accessionNumber = "";
							orderService.updateOrderFulfillerStatus(order, Order.FulfillerStatus.EXCEPTION,
								commentText, accessionNumber);
						}
						
					}
					catch (ResourceNotFoundException e) {
						log.error(
						    "Could not Fetch ServiceRequest/" + serviceRequestUuid + ":" + e.toString() + getStackTrace(e));
					}
				}
			}
		});
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
