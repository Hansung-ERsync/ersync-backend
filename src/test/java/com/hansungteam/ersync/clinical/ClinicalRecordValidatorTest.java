package com.hansungteam.ersync.clinical;

import com.hansungteam.ersync.clinical.api.ConsciousnessAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PatientAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PreKtasAssessmentRequest;
import com.hansungteam.ersync.clinical.api.TreatmentEventRequest;
import com.hansungteam.ersync.clinical.api.VitalSignSetRequest;
import com.hansungteam.ersync.clinical.application.ClinicalRecordValidator;
import com.hansungteam.ersync.clinical.domain.AgeStatus;
import com.hansungteam.ersync.clinical.domain.Avpu;
import com.hansungteam.ersync.clinical.domain.ClinicalValueState;
import com.hansungteam.ersync.clinical.domain.ConsciousnessUnassessableReason;
import com.hansungteam.ersync.clinical.domain.InjuryMechanism;
import com.hansungteam.ersync.clinical.domain.InjurySite;
import com.hansungteam.ersync.clinical.domain.MeasurementUnavailableReason;
import com.hansungteam.ersync.clinical.domain.OccurrenceType;
import com.hansungteam.ersync.clinical.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.clinical.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.clinical.domain.Sex;
import com.hansungteam.ersync.clinical.domain.Symptom;
import com.hansungteam.ersync.clinical.domain.TimeStatus;
import com.hansungteam.ersync.clinical.domain.TreatmentType;
import com.hansungteam.ersync.global.exception.CustomException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalRecordValidatorTest {

    private final ClinicalRecordValidator validator = new ClinicalRecordValidator();
    private final Instant now = Instant.parse("2026-07-29T01:00:00Z");

    @Test
    void patientAssessment_exactAgeWithoutValue_fails() {
        PatientAssessmentRequest request = patientAssessment(AgeStatus.EXACT, null, OccurrenceType.DISEASE);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void patientAssessment_nonDiseaseWithoutMechanismAndInjurySite_fails() {
        PatientAssessmentRequest request = patientAssessment(AgeStatus.ESTIMATED, 70, OccurrenceType.NON_DISEASE);

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void preKtas_levelOutsideOneToFive_fails() {
        PreKtasAssessmentRequest request = new PreKtasAssessmentRequest(
                PreKtasClassificationStatus.COMPLETED,
                6,
                null,
                null,
                now,
                "pre-ktas-2026",
                now,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void preKtas_emergencyUnfinishedWithoutReason_fails() {
        PreKtasAssessmentRequest request = new PreKtasAssessmentRequest(
                PreKtasClassificationStatus.EMERGENCY_UNFINISHED,
                null,
                null,
                null,
                now,
                "pre-ktas-2026",
                now,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void consciousness_unassessableWithoutReason_fails() {
        ConsciousnessAssessmentRequest request = new ConsciousnessAssessmentRequest(
                Avpu.UNASSESSABLE,
                null,
                null,
                now,
                now,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void vitalMeasurementUnavailableWithoutReason_fails() {
        VitalSignSetRequest request = vitalSignSet(new VitalSignSetRequest.IntegerVitalItem(
                ClinicalValueState.MEASUREMENT_UNAVAILABLE,
                null,
                null,
                null
        ));

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void treatmentNoneWithOtherTreatments_fails() {
        List<TreatmentEventRequest> requests = List.of(
                new TreatmentEventRequest(TreatmentType.NONE, now, now, "1", null, null, null),
                new TreatmentEventRequest(TreatmentType.OXYGEN, now, now, "1", "{\"method\":\"MASK\"}", null, null)
        );

        assertThatThrownBy(() -> validator.validateInitialTreatments(requests))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void correctionWithoutReason_fails() {
        PatientAssessmentRequest request = new PatientAssessmentRequest(
                AgeStatus.UNKNOWN,
                null,
                Sex.UNKNOWN,
                OccurrenceType.UNKNOWN,
                null,
                null,
                null,
                Set.of(),
                Symptom.UNKNOWN,
                null,
                Set.of(),
                TimeStatus.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                "previous-id",
                null
        );

        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(CustomException.class);
    }

    private PatientAssessmentRequest patientAssessment(
            AgeStatus ageStatus,
            Integer ageYears,
            OccurrenceType occurrenceType
    ) {
        return new PatientAssessmentRequest(
                ageStatus,
                ageYears,
                Sex.MALE,
                occurrenceType,
                null,
                occurrenceType == OccurrenceType.NON_DISEASE ? null : InjuryMechanism.UNKNOWN,
                null,
                occurrenceType == OccurrenceType.NON_DISEASE ? Set.of() : Set.of(InjurySite.UNKNOWN),
                Symptom.DYSPNEA,
                null,
                Set.of(),
                TimeStatus.UNKNOWN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                null,
                null
        );
    }

    private VitalSignSetRequest vitalSignSet(VitalSignSetRequest.IntegerVitalItem pulse) {
        VitalSignSetRequest.IntegerVitalItem valueItem = new VitalSignSetRequest.IntegerVitalItem(
                ClinicalValueState.VALUE,
                20,
                null,
                null
        );
        return new VitalSignSetRequest(
                new VitalSignSetRequest.BloodPressureItem(ClinicalValueState.VALUE, 120, 80, null, null),
                pulse,
                valueItem,
                new VitalSignSetRequest.TemperatureItem(ClinicalValueState.VALUE, BigDecimal.valueOf(36.5), null, null),
                valueItem,
                now,
                now,
                null,
                null
        );
    }
}
