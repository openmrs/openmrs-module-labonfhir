package org.openmrs.module.labonfhir.api.dao;

import org.openmrs.Encounter;
import org.openmrs.module.fhir2.Task;

public interface TaskDao {
	
	Task getTaskForEncounter(Encounter encounter);
}
