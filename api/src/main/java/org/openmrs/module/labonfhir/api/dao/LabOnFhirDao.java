package org.openmrs.module.labonfhir.api.dao;

import java.util.List;

import org.hibernate.criterion.Restrictions;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.openmrs.module.labonfhir.api.model.FailedTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository("labOnFhirDao")
public class LabOnFhirDao {
    
    @Autowired
    DbSessionFactory sessionFactory;
    
    private DbSession getSession() {
        return sessionFactory.getCurrentSession();
    }
    
    public FailedTask getFailedTaskByUuid(String uuid) {
        return (FailedTask) getSession().createCriteria(FailedTask.class).add(Restrictions.eq("uuid", uuid)).uniqueResult();
    }
    
    public FailedTask saveOrUpdateFailedTask(FailedTask failedTask) {
        getSession().saveOrUpdate(failedTask);
        return failedTask;
    }
    
    public List<FailedTask> getAllFailedTasks(Boolean isSent) {
        if (isSent != null) {
            return getSession().createCriteria(FailedTask.class).add(Restrictions.eq("isSent", isSent)).list();
        } else {
            return getSession().createCriteria(FailedTask.class).list();
        }
    }
   
}
