/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.labonfhir.api.fhir;

import static org.hibernate.criterion.Restrictions.eq;

import lombok.AccessLevel;
import lombok.Setter;
import org.hibernate.SessionFactory;
import org.openmrs.Obs;
import org.openmrs.api.db.DAOException;
import org.openmrs.module.fhir2.api.dao.FhirDiagnosticReportDao;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@Setter(AccessLevel.PACKAGE)
public class DiagnosticReportDaoImpl implements FhirDiagnosticReportDao {

	@Autowired
	@Qualifier("sessionFactory")
	SessionFactory sessionFactory;

	@Override
	public Obs getObsGroupByUuid(String uuid) {
		return (Obs) sessionFactory.getCurrentSession().createCriteria(Obs.class).createAlias("groupMembers", "group")
				.add(eq("uuid", uuid)).uniqueResult();
	}

	@Override
	public Obs saveObsGroup(Obs obsGroup) throws DAOException {
		//		if (!obs.isObsGrouping()) {
		//			throw new IllegalArgumentException("Provided Obs must be an Obs grouping.");
		//		}

		// Set comment
		obsGroup.setComment("Maps to FHIR DiagnosticReport\nSource: OpenELIS");

		sessionFactory.getCurrentSession().saveOrUpdate(obsGroup);

		return obsGroup;
	}
}
