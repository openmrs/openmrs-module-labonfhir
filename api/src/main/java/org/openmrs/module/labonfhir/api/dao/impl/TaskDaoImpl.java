package org.openmrs.module.labonfhir.api.dao.impl;

import static org.hibernate.criterion.Restrictions.eq;

import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Subqueries;
import org.hibernate.type.StringType;
import org.hibernate.type.Type;
import org.openmrs.Encounter;
import org.openmrs.Obs;
import org.openmrs.module.fhir2.Task;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.openmrs.module.labonfhir.api.dao.TaskDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("labTaskDao")
@Transactional
public class TaskDaoImpl implements TaskDao {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	@Qualifier("sessionFactory")
	private SessionFactory sessionFactory;

	@Override
	@Transactional(readOnly = true)
	public Task getTaskForEncounter(Encounter encounter) {
		DetachedCriteria detachedCriteria = DetachedCriteria.forClass(Obs.class)
				.setProjection(Projections.sqlProjection("concat('ServiceRequest/', uuid) as fhir_reference",
						new String[] { "fhir_reference" }, new Type[] { new StringType() }))
				.createAlias("concept", "c")
				.add(eq("encounter", encounter))
				.add(eq("c.conceptId", config.getTestOrderConceptUuid()));

		List<Task> tasks = sessionFactory.getCurrentSession()
				.createCriteria(org.openmrs.module.fhir2.Task.class).add(Subqueries.eq("basedOn", detachedCriteria)).list();

		if (tasks == null || tasks.size() < 1) {
			return null;
		}

		return tasks.get(0);
	}
}
