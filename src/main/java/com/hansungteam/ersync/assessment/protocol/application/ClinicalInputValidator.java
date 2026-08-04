package com.hansungteam.ersync.assessment.protocol.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 최초 요청과 이송 중 갱신에 같은 임상 조건부 규칙을 적용합니다. */
@Component
public class ClinicalInputValidator {

    public void validatePreKtas(ClinicalInput.PreKtas preKtas) {
        if (preKtas.classificationStatus() == PreKtasClassificationStatus.COMPLETED) {
            require(preKtas.level() != null && preKtas.level() >= 1 && preKtas.level() <= 5);
            require(preKtas.assessedAt() != null);
            require(preKtas.exceptionReason() == null && !hasText(preKtas.exceptionDetail()));
            return;
        }

        require(preKtas.classificationStatus() == PreKtasClassificationStatus.EMERGENCY_UNFINISHED);
        require(preKtas.level() == null && preKtas.assessedAt() == null);
        require(preKtas.exceptionReason() != null);
        require(preKtas.exceptionReason() != PreKtasExceptionReason.OTHER || hasText(preKtas.exceptionDetail()));
    }

    public void validateConsciousness(ClinicalInput.Consciousness consciousness) {
        if (consciousness.avpu() == Avpu.UNASSESSABLE) {
            require(consciousness.unassessableReason() != null);
            require(consciousness.unassessableReason()
                    != com.hansungteam.ersync.transport.domain.ConsciousnessUnassessableReason.OTHER
                    || hasText(consciousness.unassessableDetail()));
        } else {
            require(consciousness.unassessableReason() == null && !hasText(consciousness.unassessableDetail()));
        }
    }

    public void validateVitalSigns(ClinicalInput.VitalSigns vitalSigns) {
        List<ClinicalInput.VitalSign> measurements = vitalSigns.measurements();
        Set<VitalSignType> types = measurements.stream()
                .map(ClinicalInput.VitalSign::type)
                .collect(Collectors.toSet());
        require(measurements.size() == VitalSignType.values().length);
        require(types.equals(EnumSet.allOf(VitalSignType.class)));

        for (ClinicalInput.VitalSign measurement : measurements) {
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
                require(measurement.state() == VitalSignState.PATIENT_REFUSED);
                require(measurement.primaryValue() == null && measurement.secondaryValue() == null);
                require(measurement.unavailableReason() == null && !hasText(measurement.unavailableDetail()));
            }
        }
    }

    public void validateTreatments(List<ClinicalInput.Treatment> treatments, boolean allowNone) {
        long noneCount = treatments.stream().filter(treatment -> treatment.type() == TreatmentType.NONE).count();
        require(allowNone ? noneCount == 0 || (noneCount == 1 && treatments.size() == 1) : noneCount == 0);
        for (ClinicalInput.Treatment treatment : treatments) {
            validateTreatment(treatment, allowNone);
        }
    }

    public void validateTreatment(ClinicalInput.Treatment treatment, boolean allowNone) {
        if (treatment.type() == TreatmentType.NONE) {
            require(allowNone);
            require(treatment.attemptResult() == null && treatment.performedAt() == null);
            require(treatment.details() == null || isEmpty(treatment.details()));
            return;
        }
        require(treatment.attemptResult() != null && treatment.performedAt() != null);
        requireRequiredDetails(treatment.type(), treatment.details());
    }

    private void requireRequiredDetails(TreatmentType type, ClinicalInput.TreatmentDetails details) {
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

    private boolean isEmpty(ClinicalInput.TreatmentDetails details) {
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
