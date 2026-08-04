package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.assessment.protocol.application.ClinicalInputMapper;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInputValidator;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.transport.api.UpdateTreatmentRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.application.TransportRequestFingerprint;
import com.hansungteam.ersync.transport.application.TransportUpdateFingerprint;
import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClinicalUpdateInputTest {

    private final ClinicalInputValidator validator = new ClinicalInputValidator();

    @Test
    void followUpVitalSignsUseTheSameFiveMeasurementRulesAsInitialAssessment() {
        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        var update = new UpdateVitalSignsRequest(
                initial.measuredAt(),
                initial.enteredAt(),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(),
                        measurement.state(),
                        measurement.primaryValue(),
                        measurement.secondaryValue(),
                        measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );

        assertThatCode(() -> validator.validateVitalSigns(ClinicalInputMapper.from(update)))
                .doesNotThrowAnyException();
    }

    @Test
    void followUpTreatmentPreservesFailedAttemptButRejectsNone() {
        var details = new UpdateTreatmentRequest.TreatmentDetailsInput(
                null, null, null, null, false, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, "failed attempt"
        );
        var failed = new UpdateTreatmentRequest(
                TreatmentType.OTHER,
                TreatmentAttemptResult.FAILURE,
                details,
                Instant.parse("2026-08-04T01:00:00Z"),
                Instant.parse("2026-08-04T01:00:10Z")
        );

        assertThatCode(() -> validator.validateTreatment(ClinicalInputMapper.from(failed), false))
                .doesNotThrowAnyException();

        var none = new UpdateTreatmentRequest(
                TreatmentType.NONE,
                null,
                null,
                null,
                Instant.parse("2026-08-04T01:00:10Z")
        );
        assertThatThrownBy(() -> validator.validateTreatment(ClinicalInputMapper.from(none), false))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_001");
    }

    @Test
    void updateFingerprintIsStableButIncludesCommandType() {
        var fingerprint = new TransportUpdateFingerprint(new TransportRequestFingerprint());
        var request = new UpdateTreatmentRequest(
                TreatmentType.OTHER,
                TreatmentAttemptResult.SUCCESS,
                new UpdateTreatmentRequest.TreatmentDetailsInput(
                        null, null, null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, " test "
                ),
                Instant.parse("2026-08-04T01:00:00Z"),
                Instant.parse("2026-08-04T01:00:10Z")
        );

        byte[] first = fingerprint.digest("TREATMENT", request);
        byte[] replay = fingerprint.digest("TREATMENT", request);
        byte[] otherCommand = fingerprint.digest("CONSCIOUSNESS", request);

        assertThat(replay).containsExactly(first);
        assertThat(otherCommand).isNotEqualTo(first);
    }
}
