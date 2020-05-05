package org.openmrs.module.labonfhir.api.fhir;


import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import ca.uhn.fhir.rest.server.exceptions.MethodNotAllowedException;
import lombok.AccessLevel;
import lombok.Setter;
import lombok.SneakyThrows;
import org.hibernate.ObjectNotFoundException;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.module.fhir2.FhirConstants;
import org.openmrs.module.fhir2.api.FhirConceptService;
import org.openmrs.module.fhir2.api.FhirDiagnosticReportService;
import org.openmrs.module.fhir2.api.FhirObservationService;
import org.openmrs.module.fhir2.api.FhirTaskService;
import org.openmrs.module.fhir2.api.dao.FhirDiagnosticReportDao;
import org.openmrs.module.fhir2.api.dao.FhirObservationDao;
import org.openmrs.module.fhir2.api.translators.ConceptTranslator;
import org.openmrs.module.fhir2.api.translators.DiagnosticReportTranslator;
import org.openmrs.module.fhir2.api.translators.ObservationReferenceTranslator;
import org.openmrs.module.fhir2.api.translators.ObservationTranslator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRActivator;
import org.openmrs.module.labonfhir.ISantePlusLabOnFHIRConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Primary
@Component
@Transactional
@Setter(AccessLevel.PACKAGE)
public class DiagnosticReportServiceImpl implements FhirDiagnosticReportService {

	@Autowired
	FhirDiagnosticReportDao diagnosticReportDao;

	@Autowired
	DiagnosticReportTranslator diagnosticReportTranslator;

	@Autowired
	FhirObservationService observationService;

	@Autowired
	ObservationTranslator observationTranslator;

	@Autowired
	FhirObservationDao observationDao;

	@Autowired
	ObservationReferenceTranslator observationReferenceTranslator;

	@Autowired
	ConceptTranslator conceptTranslator;

	@Autowired
	FhirConceptService conceptService;

	@Autowired
	ISantePlusLabOnFHIRConfig config;

	private static final Logger log = LoggerFactory.getLogger(DiagnosticReportServiceImpl.class);

	@Override
	public DiagnosticReport getDiagnosticReportByUuid(String uuid) {
		return diagnosticReportTranslator.toFhirResource(diagnosticReportDao.getObsGroupByUuid(uuid));
	}

	@SneakyThrows
	@Override
	public DiagnosticReport saveDiagnosticReport(DiagnosticReport openelisDiagnosticReport) {
		Set<Obs> resultObs = Collections.EMPTY_SET;

		// Create and init OpenMRS DiagnosticReport
		DiagnosticReport openmrsDiagnosticReport = new DiagnosticReport();
		Observation openmrsObservation = new Observation();

		openmrsDiagnosticReport.setStatus(openelisDiagnosticReport.getStatus());
		if(openelisDiagnosticReport.hasSubject() && openelisDiagnosticReport.getSubject().getResource() != null) {
			openmrsDiagnosticReport.setSubject(setSubjectReference((Patient) openelisDiagnosticReport.getSubject().getResource()));
		}

		// TODO: only needed if ObsDateTime is fixed in the Translator
		if(openelisDiagnosticReport.hasEffective()) {
			openmrsDiagnosticReport.setEffective(openelisDiagnosticReport.getEffective());
		} else {
			openmrsDiagnosticReport.setEffective(new DateTimeType().setValue(new Date()));
		}

		// Resolve Code
		Optional<Concept> code = conceptService.getConceptByUuid(config.getDiagnosticReportConceptUuid());
		if(code.isPresent()) {
			openmrsDiagnosticReport.setCode(conceptTranslator.toFhirResource(code.get()));
		} else {
			log.error("Could not find DiagnosticReport Datatype!");
			throw new Exception("Could not find DiagnosticReport Datatype");
		}

		// Create and init OpenMRS Observation
		// ToDo - generalize not just for FirstRep
		if(openelisDiagnosticReport.hasResult() && openelisDiagnosticReport.getResultFirstRep().getResource() != null) {
			Observation openelisObservation = (Observation) openelisDiagnosticReport.getResultFirstRep().getResource();

			openelisObservation.copyValues(openmrsObservation);

			// Fix for missing Datetime
			if (openmrsObservation.getEffectiveDateTimeType().isEmpty()) {
				openmrsObservation.setEffective(new DateTimeType().setValue(new Date()));
			}

			// Resolve subject
			if (openelisObservation.hasSubject() && openelisObservation.getSubject().getResource() != null) {
				openmrsObservation.setSubject(setSubjectReference((Patient) openelisObservation.getSubject().getResource()));
			}
		}

		// Translate to OpenMRS
		Obs obsGroup = null;
		Obs obs = null;

		try {
			obsGroup = diagnosticReportTranslator.toOpenmrsType(openmrsDiagnosticReport);
			obs = observationTranslator.toOpenmrsType(openmrsObservation);
		} catch (Exception e) {
			log.error("Could not translate!");
		}

		// Set hierarchy
		obsGroup.setGroupMembers(Collections.singleton(obs));
		obs.setObsGroup(obsGroup);

		// Send to Dao
		diagnosticReportDao.saveObsGroup(obsGroup);

		return diagnosticReportTranslator.toFhirResource(obsGroup);
	}

	@Override
	public DiagnosticReport updateDiagnosticReport(String uuid, DiagnosticReport diagnosticReport) {
		if (diagnosticReport.getId() == null) {
			throw new InvalidRequestException("Diagnostic Report resource is missing id.");
		}

		if (!diagnosticReport.getId().equals(uuid)) {
			throw new InvalidRequestException("Diagnostic Report id and provided id do not match.");
		}

		Obs obsGroup = new Obs();

		if (uuid != null) {
			obsGroup = diagnosticReportDao.getObsGroupByUuid(uuid);
		}

		if (obsGroup == null) {
			throw new MethodNotAllowedException("No Diagnostic Report found to update.");
		}

		return diagnosticReportTranslator.toFhirResource(diagnosticReportDao.saveObsGroup(
				diagnosticReportTranslator.toOpenmrsType(obsGroup, diagnosticReport)));
	}

	private Reference setSubjectReference(Patient subject) {
		List<Identifier> ids = (subject.getIdentifier()
				.stream().filter(i -> i.getSystem().contains("isanteplus"))
				.collect(Collectors.toList()));

		if(!ids.isEmpty()) {
			return new Reference()
					.setReference(FhirConstants.PATIENT + "/" + ids.get(0).getValue())
					.setType(FhirConstants.PATIENT);
		} else {
			return null;
		}
	}
}
