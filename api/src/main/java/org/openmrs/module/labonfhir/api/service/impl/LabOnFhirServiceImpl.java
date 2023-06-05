package org.openmrs.module.labonfhir.api.service.impl;

import java.util.List;

import org.openmrs.api.APIException;
import org.openmrs.module.labonfhir.api.dao.LabOnFhirDao;
import org.openmrs.module.labonfhir.api.model.FailedTask;
import org.openmrs.module.labonfhir.api.service.LabOnFhirService;
import org.springframework.beans.factory.annotation.Autowired;

public class LabOnFhirServiceImpl implements LabOnFhirService{
    @Autowired
	LabOnFhirDao dao;

    @Override
    public FailedTask getFailedTaskByUuid(String uuid) throws APIException {
        return dao.getFailedTaskByUuid(uuid);
    }

    @Override
    public FailedTask saveOrUpdateFailedTask(FailedTask failedTask) throws APIException {
        return dao.saveOrUpdateFailedTask(failedTask);
    }

    @Override
    public List<FailedTask> getAllFailedTasks(Boolean isSent) throws APIException {
        return dao.getAllFailedTasks(isSent);
    }

    @Override
    public void onStartup() {
       
    }

    @Override
    public void onShutdown() {
    
    }
    
}
