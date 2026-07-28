package com.hansungteam.ersync.clinical.application;

import com.hansungteam.ersync.clinical.api.ConsciousnessAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PatientAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PreKtasAssessmentRequest;
import com.hansungteam.ersync.clinical.api.TreatmentEventRequest;
import com.hansungteam.ersync.clinical.api.VitalSignSetRequest;
import com.hansungteam.ersync.clinical.domain.AgeStatus;
import com.hansungteam.ersync.clinical.domain.Avpu;
import com.hansungteam.ersync.clinical.domain.ClinicalValueState;
import com.hansungteam.ersync.clinical.domain.ConsciousnessUnassessableReason;
import com.hansungteam.ersync.clinical.domain.InjuryMechanism;
import com.hansungteam.ersync.clinical.domain.MeasurementUnavailableReason;
import com.hansungteam.ersync.clinical.domain.OccurrenceType;
import com.hansungteam.ersync.clinical.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.clinical.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.clinical.domain.Symptom;
import com.hansungteam.ersync.clinical.domain.TimeStatus;
import com.hansungteam.ersync.clinical.domain.TreatmentType;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Bean Validation만으로 표현하기 어려운 임상 DTO의 교차 필드 정책을 검증합니다.
 */
@Component
public class ClinicalRecordValidator {

    public void validate(PatientAssessmentRequest request) {
        requireCorrectionPair(request.supersedesAssessmentId(), request.correctionReason());
        validateAge(request.ageStatus(), request.ageYears());
        if (request.occurrenceType() == OccurrenceType.OTHER) {
            requireDetail(request.occurrenceOtherDetail());
        }
        if (request.occurrenceType() == OccurrenceType.NON_DISEASE) {
            requirePresent(request.mechanism());
            requireNonEmpty(request.injurySites());
        }
        if (request.mechanism() == InjuryMechanism.OTHER) {
            requireDetail(request.mechanismOtherDetail());
        }
        if (request.primarySymptom() == Symptom.OTHER) {
            requireDetail(request.primarySymptomOtherDetail());
        }
        validateTime(request.onsetTimeStatus(), request.onsetAt());
        validateOptionalTime(request.lastKnownWellStatus(), request.lastKnownWellAt());
        validateOptionalTime(request.accidentTimeStatus(), request.accidentAt());
        validateOptionalTime(request.cardiacArrestTimeStatus(), request.cardiacArrestAt());
    }

    public void validate(PreKtasAssessmentRequest request) {
        requireCorrectionPair(request.supersedesAssessmentId(), request.correctionReason());
        if (request.classificationStatus() == PreKtasClassificationStatus.COMPLETED) {
            if (request.level() == null || request.level() < 1 || request.level() > 5) {
                fail();
            }
            if (request.exceptionReason() != null || hasText(request.exceptionDetail())) {
                fail();
            }
            return;
        }

        if (request.level() != null || request.exceptionReason() == null) {
            fail();
        }
        if (request.exceptionReason() == PreKtasExceptionReason.OTHER) {
            requireDetail(request.exceptionDetail());
        }
    }

    public void validate(ConsciousnessAssessmentRequest request) {
        requireCorrectionPair(request.supersedesAssessmentId(), request.correctionReason());
        if (request.avpu() == Avpu.UNASSESSABLE) {
            requirePresent(request.unassessableReason());
            if (request.unassessableReason() == ConsciousnessUnassessableReason.OTHER) {
                requireDetail(request.unassessableDetail());
            }
            return;
        }
        if (request.unassessableReason() != null || hasText(request.unassessableDetail())) {
            fail();
        }
    }

    public void validate(VitalSignSetRequest request) {
        requireCorrectionPair(request.supersedesVitalSignSetId(), request.correctionReason());
        validateBloodPressure(request.bloodPressure());
        validateIntegerVital(request.pulse());
        validateIntegerVital(request.respiratoryRate());
        validateTemperature(request.temperature());
        validateIntegerVital(request.spo2());
    }

    public void validateInitialTreatments(List<TreatmentEventRequest> requests) {
        requireNonEmpty(requests);
        boolean hasNone = requests.stream().anyMatch(request -> request.type() == TreatmentType.NONE);
        if (hasNone && requests.size() > 1) {
            fail();
        }
        requests.forEach(this::validate);
    }

    public void validate(TreatmentEventRequest request) {
        requireCorrectionPair(request.supersedesTreatmentEventId(), request.correctionReason());
        if (request.type() == TreatmentType.NONE) {
            if (hasText(request.detailsJson())) {
                fail();
            }
            return;
        }
        requireDetail(request.detailsJson());
    }

    private void validateAge(AgeStatus ageStatus, Integer ageYears) {
        if ((ageStatus == AgeStatus.EXACT || ageStatus == AgeStatus.ESTIMATED)
                && (ageYears == null || ageYears < 0 || ageYears > 130)) {
            fail();
        }
        if (ageStatus == AgeStatus.UNKNOWN && ageYears != null) {
            fail();
        }
    }

    private void validateTime(TimeStatus status, Instant value) {
        if ((status == TimeStatus.EXACT || status == TimeStatus.ESTIMATED) && value == null) {
            fail();
        }
        if (status == TimeStatus.UNKNOWN && value != null) {
            fail();
        }
    }

    private void validateOptionalTime(TimeStatus status, Instant value) {
        if (status == null && value == null) {
            return;
        }
        requirePresent(status);
        validateTime(status, value);
    }

    private void validateBloodPressure(VitalSignSetRequest.BloodPressureItem item) {
        if (item.state() == ClinicalValueState.VALUE) {
            if (item.systolic() == null || item.diastolic() == null
                    || item.unavailableReason() != null || hasText(item.otherDetail())) {
                fail();
            }
            return;
        }
        if (item.systolic() != null || item.diastolic() != null) {
            fail();
        }
        validateUnavailableState(item.state(), item.unavailableReason(), item.otherDetail());
    }

    private void validateIntegerVital(VitalSignSetRequest.IntegerVitalItem item) {
        if (item.state() == ClinicalValueState.VALUE) {
            if (item.value() == null || item.unavailableReason() != null || hasText(item.otherDetail())) {
                fail();
            }
            return;
        }
        if (item.value() != null) {
            fail();
        }
        validateUnavailableState(item.state(), item.unavailableReason(), item.otherDetail());
    }

    private void validateTemperature(VitalSignSetRequest.TemperatureItem item) {
        if (item.state() == ClinicalValueState.VALUE) {
            if (item.value() == null || item.unavailableReason() != null || hasText(item.otherDetail())) {
                fail();
            }
            return;
        }
        if (item.value() != null) {
            fail();
        }
        validateUnavailableState(item.state(), item.unavailableReason(), item.otherDetail());
    }

    private void validateUnavailableState(
            ClinicalValueState state,
            MeasurementUnavailableReason reason,
            String otherDetail
    ) {
        if (state == ClinicalValueState.MEASUREMENT_UNAVAILABLE) {
            requirePresent(reason);
            if (reason == MeasurementUnavailableReason.OTHER) {
                requireDetail(otherDetail);
            }
            return;
        }
        if (reason != null || hasText(otherDetail)) {
            fail();
        }
    }

    private void requireCorrectionPair(String supersedesRecordId, String correctionReason) {
        if (hasText(supersedesRecordId) != hasText(correctionReason)) {
            fail();
        }
    }

    private void requireDetail(String value) {
        if (!hasText(value)) {
            fail();
        }
    }

    private void requirePresent(Object value) {
        if (value == null) {
            fail();
        }
    }

    private void requireNonEmpty(Collection<?> value) {
        if (value == null || value.isEmpty()) {
            fail();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void fail() {
        throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
    }
}
