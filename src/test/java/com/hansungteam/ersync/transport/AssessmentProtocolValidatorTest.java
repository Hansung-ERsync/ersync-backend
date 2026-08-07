package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.assessment.protocol.application.AssessmentProtocolRegistry;
import com.hansungteam.ersync.assessment.protocol.application.AssessmentProtocolValidator;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInputValidator;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest;
import com.hansungteam.ersync.transport.domain.AgeStatus;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.transport.domain.PatientSex;
import com.hansungteam.ersync.transport.domain.PupilResponse;
import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentProtocolValidatorTest {

    private final AssessmentProtocolValidator validator = new AssessmentProtocolValidator(
            new AssessmentProtocolRegistry("ERSYNC_MVP_1.0", "DEV_UNCONFIRMED"),
            new ClinicalInputValidator()
    );

    @Test
    void completeDevelopmentAssessmentIsAccepted() {
        assertThatCode(() -> validator.validate(ValidTransportRequestFixtures.request()))
                .doesNotThrowAnyException();
    }

    @Test
    void completeSupplementalAssessmentIsAccepted() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        var supplemental = supplemental(
                85,
                PupilResponse.NORMAL,
                PupilResponse.SLUGGISH,
                " 고혈압 ",
                "확인된 알레르기 없음",
                "혈압약",
                false
        );

        assertThatCode(() -> validator.validate(copy(valid, supplemental)))
                .doesNotThrowAnyException();
    }

    @Test
    void eachSupplementalAssessmentKindCanBeRecordedByItself() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        List<CreateTransportRequestRequest.SupplementalAssessmentInput> individualInputs = List.of(
                supplemental(85, null, null, null, null, null, null),
                supplemental(null, PupilResponse.NORMAL, PupilResponse.UNASSESSABLE, null, null, null, null),
                supplemental(null, null, null, "고혈압", null, null, null),
                supplemental(null, null, null, null, "페니실린", null, null),
                supplemental(null, null, null, null, null, "혈압약", null),
                supplemental(null, null, null, null, null, null, false)
        );

        individualInputs.forEach(input -> assertThatCode(() -> validator.validate(copy(valid, input)))
                .doesNotThrowAnyException());
    }

    @Test
    void emptySupplementalAssessmentIsRejected() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();

        assertThatThrownBy(() -> validator.validate(copy(valid, supplemental(
                null, null, null, null, null, null, null
        ))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void pupilsMustBeRecordedAsAPair() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();

        assertThatThrownBy(() -> validator.validate(copy(valid, supplemental(
                null, PupilResponse.NORMAL, null, null, null, null, null
        ))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void blankSupplementalTextIsRejected() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();

        assertThatThrownBy(() -> validator.validate(copy(valid, supplemental(
                85, null, null, "   ", null, null, null
        ))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1001})
    void outOfRangeSupplementalGlucoseIsRejected(int glucose) {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();

        assertThatThrownBy(() -> validator.validate(copy(valid, supplemental(
                glucose, null, null, null, null, null, null
        ))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void oversizedSupplementalTextIsRejectedAfterTrim() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();

        assertThatThrownBy(() -> validator.validate(copy(valid, supplemental(
                null, null, null, "가".repeat(121), null, null, null
        ))))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void supplementalAssessmentCannotBeAfterItsInputTime() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        var supplemental = new CreateTransportRequestRequest.SupplementalAssessmentInput(
                Instant.parse("2026-08-03T10:02:00Z"),
                Instant.parse("2026-08-03T10:01:00Z"),
                85,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(() -> validator.validate(copy(valid, supplemental)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void unknownAgeCannotContainAnAgeValue() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        CreateTransportRequestRequest invalid = new CreateTransportRequestRequest(
                valid.assessmentProtocolVersion(),
                valid.origin(),
                new CreateTransportRequestRequest.PatientInput(AgeStatus.UNKNOWN, 45, PatientSex.UNKNOWN),
                valid.incident(),
                valid.preKtas(),
                valid.consciousness(),
                valid.vitalSigns(),
                valid.treatments()
        );

        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void inactiveProtocolVersionIsRejected() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        CreateTransportRequestRequest invalid = new CreateTransportRequestRequest(
                "ERSYNC_MVP_2.0",
                valid.origin(),
                valid.patient(),
                valid.incident(),
                valid.preKtas(),
                valid.consciousness(),
                valid.vitalSigns(),
                valid.treatments()
        );

        assertThatThrownBy(() -> validator.validate(invalid))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("PROTOCOL_002");
    }

    @Test
    void emergencyUnfinishedPreKtasWithReasonIsAccepted() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        CreateTransportRequestRequest emergency = copy(
                valid,
                new CreateTransportRequestRequest.PreKtasInput(
                        PreKtasClassificationStatus.EMERGENCY_UNFINISHED,
                        null,
                        PreKtasExceptionReason.CPR_IN_PROGRESS,
                        null,
                        null,
                        "DEV_UNCONFIRMED",
                        Instant.parse("2026-08-03T10:01:00Z")
                ),
                valid.vitalSigns(),
                valid.treatments()
        );

        assertThatCode(() -> validator.validate(emergency)).doesNotThrowAnyException();
    }

    @Test
    void unavailableAndRefusedVitalSignsAreAcceptedButDuplicateTypeIsRejected() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        List<CreateTransportRequestRequest.VitalSignInput> measurements = new ArrayList<>(
                valid.vitalSigns().measurements()
        );
        measurements.set(1, new CreateTransportRequestRequest.VitalSignInput(
                VitalSignType.PULSE,
                VitalSignState.MEASUREMENT_UNAVAILABLE,
                null,
                null,
                VitalSignUnavailableReason.DEVICE_ERROR,
                null
        ));
        measurements.set(2, new CreateTransportRequestRequest.VitalSignInput(
                VitalSignType.RESPIRATORY_RATE,
                VitalSignState.PATIENT_REFUSED,
                null,
                null,
                null,
                null
        ));
        var validStates = new CreateTransportRequestRequest.VitalSignsInput(
                valid.vitalSigns().measuredAt(),
                valid.vitalSigns().enteredAt(),
                measurements
        );
        assertThatCode(() -> validator.validate(copy(valid, valid.preKtas(), validStates, valid.treatments())))
                .doesNotThrowAnyException();

        measurements.set(4, new CreateTransportRequestRequest.VitalSignInput(
                VitalSignType.PULSE,
                VitalSignState.VALUE,
                new BigDecimal("98"),
                null,
                null,
                null
        ));
        var duplicate = new CreateTransportRequestRequest.VitalSignsInput(
                valid.vitalSigns().measuredAt(),
                valid.vitalSigns().enteredAt(),
                measurements
        );
        assertThatThrownBy(() -> validator.validate(copy(valid, valid.preKtas(), duplicate, valid.treatments())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void noneCannotBeMixedWithARealTreatment() {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        var oxygenDetails = new CreateTransportRequestRequest.TreatmentDetailsInput(
                "nasal-cannula", null, new BigDecimal("4"), null, null, null,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );
        var oxygen = new CreateTransportRequestRequest.TreatmentInput(
                TreatmentType.OXYGEN,
                TreatmentAttemptResult.SUCCESS,
                oxygenDetails,
                Instant.parse("2026-08-03T10:00:30Z"),
                Instant.parse("2026-08-03T10:01:00Z")
        );

        assertThatThrownBy(() -> validator.validate(copy(
                valid,
                valid.preKtas(),
                valid.vitalSigns(),
                List.of(valid.treatments().getFirst(), oxygen)
        )))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @ParameterizedTest
    @EnumSource(value = TreatmentType.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
    void eachTreatmentTypeAcceptsItsMinimumTypedDetailsAndFailureResult(TreatmentType type) {
        CreateTransportRequestRequest valid = ValidTransportRequestFixtures.request();
        var treatment = new CreateTransportRequestRequest.TreatmentInput(
                type,
                TreatmentAttemptResult.FAILURE,
                minimumDetails(type),
                Instant.parse("2026-08-03T10:00:30Z"),
                Instant.parse("2026-08-03T10:01:00Z")
        );

        assertThatCode(() -> validator.validate(copy(
                valid,
                valid.preKtas(),
                valid.vitalSigns(),
                List.of(treatment)
        ))).doesNotThrowAnyException();
    }

    private CreateTransportRequestRequest.TreatmentDetailsInput minimumDetails(TreatmentType type) {
        String method = null;
        String device = null;
        BigDecimal flowRateLpm = null;
        Instant startedAt = null;
        String currentStatus = null;
        Integer shockCount = null;
        String fluidName = null;
        BigDecimal amountMl = null;
        String medicationName = null;
        String dose = null;
        String route = null;
        String site = null;
        String leadType = null;
        Instant birthAt = null;
        String detail = null;

        switch (type) {
            case OXYGEN -> {
                method = "nasal-cannula";
                flowRateLpm = new BigDecimal("4");
            }
            case AIRWAY -> device = "oropharyngeal-airway";
            case CPR -> {
                startedAt = Instant.parse("2026-08-03T09:59:00Z");
                currentStatus = "ongoing";
            }
            case DEFIBRILLATION_AED -> shockCount = 1;
            case IV_FLUID -> {
                fluidName = "test-fluid";
                amountMl = new BigDecimal("100");
            }
            case MEDICATION -> {
                medicationName = "test-medication";
                dose = "test-dose";
                route = "test-route";
            }
            case BLEEDING_WOUND, IMMOBILIZATION -> {
                method = "test-method";
                site = "test-site";
            }
            case ECG -> leadType = "12-lead";
            case WARMING_COOLING -> method = "test-method";
            case DELIVERY -> birthAt = Instant.parse("2026-08-03T10:00:00Z");
            case OTHER -> detail = "test-detail";
            case NONE -> throw new IllegalArgumentException("NONE does not use details");
        }

        return new CreateTransportRequestRequest.TreatmentDetailsInput(
                method,
                device,
                flowRateLpm,
                startedAt,
                null,
                currentStatus,
                null,
                null,
                shockCount,
                fluidName,
                amountMl,
                medicationName,
                dose,
                route,
                site,
                null,
                null,
                leadType,
                null,
                null,
                birthAt,
                detail
        );
    }

    private CreateTransportRequestRequest copy(
            CreateTransportRequestRequest source,
            CreateTransportRequestRequest.PreKtasInput preKtas,
            CreateTransportRequestRequest.VitalSignsInput vitalSigns,
            List<CreateTransportRequestRequest.TreatmentInput> treatments
    ) {
        return new CreateTransportRequestRequest(
                source.assessmentProtocolVersion(),
                source.origin(),
                source.patient(),
                source.incident(),
                preKtas,
                source.consciousness(),
                vitalSigns,
                treatments
        );
    }

    private CreateTransportRequestRequest copy(
            CreateTransportRequestRequest source,
            CreateTransportRequestRequest.SupplementalAssessmentInput supplemental
    ) {
        return new CreateTransportRequestRequest(
                source.assessmentProtocolVersion(),
                source.origin(),
                source.patient(),
                source.incident(),
                source.preKtas(),
                source.consciousness(),
                source.vitalSigns(),
                source.treatments(),
                supplemental
        );
    }

    private CreateTransportRequestRequest.SupplementalAssessmentInput supplemental(
            Integer glucoseMgDl,
            PupilResponse leftPupil,
            PupilResponse rightPupil,
            String medicalHistory,
            String allergies,
            String medications,
            Boolean isolationConcern
    ) {
        return new CreateTransportRequestRequest.SupplementalAssessmentInput(
                Instant.parse("2026-08-03T10:00:00Z"),
                Instant.parse("2026-08-03T10:01:00Z"),
                glucoseMgDl,
                leftPupil,
                rightPupil,
                medicalHistory,
                allergies,
                medications,
                isolationConcern
        );
    }
}
