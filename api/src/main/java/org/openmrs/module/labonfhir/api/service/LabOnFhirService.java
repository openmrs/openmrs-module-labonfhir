package org.openmrs.module.labonfhir.api.service;

import org.openmrs.api.OpenmrsService;
import org.openmrs.module.labonfhir.api.model.FailedTask;

import java.util.List;
import org.openmrs.api.APIException;
import org.springframework.transaction.annotation.Transactional;


public interface LabOnFhirService extends OpenmrsService{
    /**
	 * Returns FailedTask by uuid
	 * 
	 * @param uuid
	 * @return FailedTask
	 * @throws APIException
	 */
	//@Authorized()
	@Transactional(readOnly = true)
	FailedTask getFailedTaskByUuid(String uuid) throws APIException;
	
	/**
	 * Saves an FailedTask
	 * 
	 * @param failedTask
	 * @return FailedTask
	 * @throws APIException
	 */
	@Transactional
	FailedTask saveOrUpdateFailedTask(FailedTask failedTask) throws APIException;
	
	/**
	 * Returns Unsent or Sent FailedTask . if the isSent param equals null , it returns all Failed Tasks
     * 
	 * @param isSent
	 * @return List of FailedTasks
	 * @throws APIException
	 */
	@Transactional
	List<FailedTask> getAllFailedTasks(Boolean isSent) throws APIException;

    
}
