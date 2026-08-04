package com.hansungteam.ersync.assessment.protocol.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.IncidentInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.PatientInput;
import com.hansungteam.ersync.transport.domain.AgeStatus;
import com.hansungteam.ersync.transport.domain.OccurrenceType;
import com.hansungteam.ersync.transport.domain.OnsetTimeStatus;
import com.hansungteam.ersync.transport.domain.Symptom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/** `ERSYNC_MVP_1.0`의 최초 평가와 공통 임상 조건부 규칙을 검증합니다. */
@Component
@RequiredArgsConstructor
public class AssessmentProtocolValidator {

    private final AssessmentProtocolRegistry registry;
    private final ClinicalInputValidator clinicalInputValidator;

    public void validate(CreateTransportRequestRequest request) {
        registry.requireActive(request.assessmentProtocolVersion());
        registry.requirePreKtasStandardVersion(request.preKtas().standardVersion());
        validatePatient(request.patient());
        validateIncident(request.incident());
        clinicalInputValidator.validatePreKtas(ClinicalInputMapper.from(request.preKtas()));
        clinicalInputValidator.validateConsciousness(ClinicalInputMapper.from(request.consciousness()));
        clinicalInputValidator.validateVitalSigns(ClinicalInputMapper.from(request.vitalSigns()));
        clinicalInputValidator.validateTreatments(ClinicalInputMapper.from(request.treatments()), true);
    }

    private void validatePatient(PatientInput patient) {
        boolean knownAge = patient.ageStatus() == AgeStatus.EXACT || patient.ageStatus() == AgeStatus.ESTIMATED;
        require(knownAge == (patient.ageYears() != null));
    }

    private void validateIncident(IncidentInput incident) {
        Set<?> injurySites = incident.injurySites() == null ? Set.of() : incident.injurySites();
        Set<?> secondarySymptoms = incident.secondarySymptoms() == null ? Set.of() : incident.secondarySymptoms();
        require(secondarySymptoms.size() == (incident.secondarySymptoms() == null ? 0 : incident.secondarySymptoms().size()));

        if (incident.occurrenceType() == OccurrenceType.NON_DISEASE) {
            require(incident.mechanism() != null && !injurySites.isEmpty());
        } else {
            require(incident.mechanism() == null && injurySites.isEmpty());
        }
        require(incident.occurrenceType() != OccurrenceType.OTHER || hasText(incident.occurrenceDetail()));
        require(incident.primarySymptom() != Symptom.OTHER || hasText(incident.primarySymptomDetail()));

        boolean knownOnset = incident.onsetTimeStatus() == OnsetTimeStatus.EXACT
                || incident.onsetTimeStatus() == OnsetTimeStatus.ESTIMATED;
        require(knownOnset == (incident.onsetAt() != null));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void require(boolean condition) {
        if (!condition) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
    }
}
