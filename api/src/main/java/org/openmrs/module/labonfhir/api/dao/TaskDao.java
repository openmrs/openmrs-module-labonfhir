package org.openmrs.module.labonfhir.api.dao;

import org.openmrs.Encounter;
import org.openmrs.module.fhir2.FhirTask;

public interface TaskDao {
	
	FhirTask getTaskForEncounter(Encounter encounter);
}
