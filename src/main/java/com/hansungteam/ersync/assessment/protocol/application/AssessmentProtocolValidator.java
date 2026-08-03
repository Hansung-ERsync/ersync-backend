package com.hansungteam.ersync.assessment.protocol.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.ConsciousnessInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.IncidentInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.PatientInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.PreKtasInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.TreatmentDetailsInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.TreatmentInput;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.VitalSignInput;
import com.hansungteam.ersync.transport.domain.AgeStatus;
import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.OccurrenceType;
import com.hansungteam.ersync.transport.domain.OnsetTimeStatus;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.transport.domain.Symptom;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** `ERSYNC_MVP_1.0`의 조건부 필드와 상호 배타 규칙을 검증합니다. */
@Component
@RequiredArgsConstructor
public class AssessmentProtocolValidator {

    private final AssessmentProtocolRegistry registry;

    public void validate(CreateTransportRequestRequest request) {
        registry.requireActive(request.assessmentProtocolVersion());
        registry.requirePreKtasStandardVersion(request.preKtas().standardVersion());
        validatePatient(request.patient());
        validateIncident(request.incident());
        validatePreKtas(request.preKtas());
        validateConsciousness(request.consciousness());
        validateVitalSigns(request.vitalSigns().measurements());
        validateTreatments(request.treatments());
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

    private void validatePreKtas(PreKtasInput preKtas) {
        if (preKtas.classificationStatus() == PreKtasClassificationStatus.COMPLETED) {
            require(preKtas.level() != null && preKtas.level() >= 1 && preKtas.level() <= 5);
            require(preKtas.assessedAt() != null);
            require(preKtas.exceptionReason() == null && !hasText(preKtas.exceptionDetail()));
            return;
        }

        require(preKtas.level() == null && preKtas.assessedAt() == null);
        require(preKtas.exceptionReason() != null);
        require(preKtas.exceptionReason() != PreKtasExceptionReason.OTHER || hasText(preKtas.exceptionDetail()));
    }

    private void validateConsciousness(ConsciousnessInput consciousness) {
        if (consciousness.avpu() == Avpu.UNASSESSABLE) {
            require(consciousness.unassessableReason() != null);
            require(consciousness.unassessableReason() != com.hansungteam.ersync.transport.domain.ConsciousnessUnassessableReason.OTHER
                    || hasText(consciousness.unassessableDetail()));
        } else {
            require(consciousness.unassessableReason() == null && !hasText(consciousness.unassessableDetail()));
        }
    }

    private void validateVitalSigns(List<VitalSignInput> measurements) {
        Set<VitalSignType> types = measurements.stream()
                .map(VitalSignInput::type)
                .collect(Collectors.toSet());
        require(measurements.size() == VitalSignType.values().length);
        require(types.equals(EnumSet.allOf(VitalSignType.class)));

        for (VitalSignInput measurement : measurements) {
            if (measurement.state() == VitalSignState.VALUE) {
                require(measurement.primaryValue() != null);
                require(measurement.type() == VitalSignType.BLOOD_PRESSURE
                        ? measurement.secondaryValue() != null
                        : measurement.secondaryValue() == null);
                require(measurement.unavailableReason() == null && !hasText(measurement.unavailableDetail()));
            } else if (measurement.state() == VitalSignState.MEASUREMENT_UNAVAILABLE) {
                require(measurement.primaryValue() == null && measurement.secondaryValue() == null);
                require(measurement.unavailableReason() != null);
                require(measurement.unavailableReason() != VitalSignUnavailableReason.OTHER
                        || hasText(measurement.unavailableDetail()));
            } else {
                require(measurement.primaryValue() == null && measurement.secondaryValue() == null);
                require(measurement.unavailableReason() == null && !hasText(measurement.unavailableDetail()));
            }
        }
    }

    private void validateTreatments(List<TreatmentInput> treatments) {
        long noneCount = treatments.stream().filter(treatment -> treatment.type() == TreatmentType.NONE).count();
        require(noneCount == 0 || (noneCount == 1 && treatments.size() == 1));

        for (TreatmentInput treatment : treatments) {
            if (treatment.type() == TreatmentType.NONE) {
                require(treatment.attemptResult() == null && treatment.performedAt() == null);
                require(treatment.details() == null || isEmpty(treatment.details()));
                continue;
            }
            require(treatment.attemptResult() != null && treatment.performedAt() != null);
            requireRequiredDetails(treatment.type(), treatment.details());
        }
    }

    private void requireRequiredDetails(TreatmentType type, TreatmentDetailsInput details) {
        require(details != null);
        switch (type) {
            case OXYGEN -> require(hasText(details.method()) && details.flowRateLpm() != null);
            case AIRWAY -> require(hasText(details.device()));
            case CPR -> require(details.startedAt() != null && hasText(details.currentStatus()));
            case DEFIBRILLATION_AED -> require(details.shockCount() != null && details.shockCount() >= 0);
            case IV_FLUID -> require(hasText(details.fluidName()) && details.amountMl() != null);
            case MEDICATION -> require(hasText(details.medicationName()) && hasText(details.dose()) && hasText(details.route()));
            case BLEEDING_WOUND, IMMOBILIZATION -> require(hasText(details.method()) && hasText(details.site()));
            case ECG -> require(hasText(details.leadType()));
            case WARMING_COOLING -> require(hasText(details.method()));
            case DELIVERY -> require(details.birthAt() != null || hasText(details.currentStatus()));
            case OTHER -> require(hasText(details.detail()));
            case NONE -> throw new IllegalStateException("NONE is validated separately");
        }
    }

    private boolean isEmpty(TreatmentDetailsInput details) {
        return !hasText(details.method())
                && !hasText(details.device())
                && details.flowRateLpm() == null
                && details.startedAt() == null
                && details.success() == null
                && !hasText(details.currentStatus())
                && details.rosc() == null
                && details.roscAt() == null
                && details.shockCount() == null
                && !hasText(details.fluidName())
                && details.amountMl() == null
                && !hasText(details.medicationName())
                && !hasText(details.dose())
                && !hasText(details.route())
                && !hasText(details.site())
                && details.tourniquetUsed() == null
                && details.tourniquetAppliedAt() == null
                && !hasText(details.leadType())
                && !hasText(details.findings())
                && details.transmitted() == null
                && details.birthAt() == null
                && !hasText(details.detail());
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
