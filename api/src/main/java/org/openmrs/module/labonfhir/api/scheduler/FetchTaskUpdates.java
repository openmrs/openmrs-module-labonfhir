package org.openmrs.module.labonfhir.api.scheduler;

import lombok.AccessLevel;
import lombok.Setter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.module.labonfhir.api.service.FetchTaskUpdatesService;
import org.openmrs.scheduler.tasks.AbstractTask;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
@Setter(AccessLevel.PACKAGE)
public class FetchTaskUpdates extends AbstractTask implements ApplicationContextAware {
	
	private static Log log = LogFactory.getLog(FetchTaskUpdates.class);
	
	private static ApplicationContext applicationContext;
	
	@Autowired
	private FetchTaskUpdatesService fetchTaskUpdatesService;
	
	@Override
	public void execute() {
		
		try {
			applicationContext.getAutowireCapableBeanFactory().autowireBean(this);
		}
		catch (Exception e) {
			// return;
		}
		
		fetchTaskUpdatesService.fetchTaskUpdates(null);
		
		super.startExecuting();
	}
	
	@Override
	public void shutdown() {
		log.debug("shutting down FetchTaskUpdates Task");
		
		this.stopExecuting();
	}
	
	@SuppressWarnings("static-access")
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
