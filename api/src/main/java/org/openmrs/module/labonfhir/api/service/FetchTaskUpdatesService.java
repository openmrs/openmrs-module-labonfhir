package org.openmrs.module.labonfhir.api.service;

import java.util.UUID;

import javax.annotation.Nullable;

import org.openmrs.api.OpenmrsService;

public interface FetchTaskUpdatesService extends OpenmrsService {
	
	public void fetchTaskUpdates(@Nullable UUID uuid);
	
}
