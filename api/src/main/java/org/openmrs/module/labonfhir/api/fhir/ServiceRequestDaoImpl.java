package org.openmrs.module.labonfhir.api.fhir;

import static org.hibernate.criterion.Restrictions.eq;

import org.hibernate.SessionFactory;
import org.hibernate.sql.JoinType;
import org.openmrs.Obs;
import org.openmrs.module.fhir2.api.dao.FhirServiceRequestDao;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
public class ServiceRequestDaoImpl implements FhirServiceRequestDao<Obs> {

	@Autowired
	private ISantePlusLabOnFHIRConfig config;

	@Autowired
	@Qualifier("sessionFactory")
	private SessionFactory sessionFactory;

	@Override
	public Obs getServiceRequestByUuid(String uuid) {
		return (Obs) sessionFactory.getCurrentSession().createCriteria(Obs.class)
				.createAlias("concept", "c",
						JoinType.INNER_JOIN, eq("c.uuid", config.getTestOrderConceptUuid()))
				.add(eq("uuid", uuid)).add(eq("voided", false)).uniqueResult();
	}
}
