package com.hansungteam.ersync.clinical;

import com.hansungteam.ersync.clinical.api.ConsciousnessAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PatientAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PreKtasAssessmentRequest;
import com.hansungteam.ersync.clinical.api.TreatmentEventRequest;
import com.hansungteam.ersync.clinical.api.VitalSignSetRequest;
import com.hansungteam.ersync.clinical.application.ClinicalRecordAppender;
import com.hansungteam.ersync.clinical.domain.AgeStatus;
import com.hansungteam.ersync.clinical.domain.Avpu;
import com.hansungteam.ersync.clinical.domain.ClinicalValueState;
import com.hansungteam.ersync.clinical.domain.InjuryMechanism;
import com.hansungteam.ersync.clinical.domain.InjurySite;
import com.hansungteam.ersync.clinical.domain.OccurrenceType;
import com.hansungteam.ersync.clinical.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.clinical.domain.Sex;
import com.hansungteam.ersync.clinical.domain.Symptom;
import com.hansungteam.ersync.clinical.domain.TimeStatus;
import com.hansungteam.ersync.clinical.domain.TreatmentType;
import com.hansungteam.ersync.clinical.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.clinical.infrastructure.PatientAssessmentVersionEntity;
import com.hansungteam.ersync.clinical.infrastructure.PatientAssessmentVersionRepository;
import com.hansungteam.ersync.clinical.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.clinical.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.clinical.infrastructure.VitalSignSetRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ersync.bootstrap.super-admin.login-id=admin",
        "ersync.bootstrap.super-admin.password=admin-password",
        "ersync.security.jwt.secret=test-jwt-secret-which-is-long-enough"
})
@Transactional
class ClinicalRecordAppenderIntegrationTest {

    private final Instant now = Instant.parse("2026-07-29T01:00:00Z");

    @Autowired
    private ClinicalRecordAppender appender;

    @Autowired
    private PatientAssessmentVersionRepository patientAssessmentVersionRepository;

    @Autowired
    private PreKtasAssessmentRepository preKtasAssessmentRepository;

    @Autowired
    private ConsciousnessAssessmentRepository consciousnessAssessmentRepository;

    @Autowired
    private VitalSignSetRepository vitalSignSetRepository;

    @Autowired
    private TreatmentEventRepository treatmentEventRepository;

    @Test
    void appendsClinicalRecordsWithoutUpdatingExistingRows() {
        String transportRequestId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();

        PatientAssessmentVersionEntity first = appender.appendPatientAssessment(
                transportRequestId,
                accountId,
                patientAssessment(70, null, null)
        );
        PatientAssessmentVersionEntity second = appender.appendPatientAssessment(
                transportRequestId,
                accountId,
                patientAssessment(71, first.id(), "입력 정정")
        );

        appender.appendPreKtas(transportRequestId, accountId, preKtasAssessment());
        appender.appendConsciousness(transportRequestId, accountId, consciousnessAssessment());
        appender.appendVitalSignSet(transportRequestId, accountId, vitalSignSet());
        appender.appendInitialTreatments(transportRequestId, accountId, List.of(treatmentEvent()));

        assertThat(first.versionNumber()).isEqualTo(1);
        assertThat(second.versionNumber()).isEqualTo(2);
        assertThat(second.supersedesAssessmentId()).isEqualTo(first.id());
        assertThat(patientAssessmentVersionRepository.countByTransportRequestId(transportRequestId)).isEqualTo(2);
        assertThat(patientAssessmentVersionRepository.findById(first.id()))
                .hasValueSatisfying(savedFirst -> assertThat(savedFirst.ageYears()).isEqualTo(70));
        assertThat(preKtasAssessmentRepository.countByTransportRequestId(transportRequestId)).isEqualTo(1);
        assertThat(consciousnessAssessmentRepository.countByTransportRequestId(transportRequestId)).isEqualTo(1);
        assertThat(vitalSignSetRepository.countByTransportRequestId(transportRequestId)).isEqualTo(1);
        assertThat(treatmentEventRepository.countByTransportRequestId(transportRequestId)).isEqualTo(1);
    }

    private PatientAssessmentRequest patientAssessment(
            int ageYears,
            String supersedesAssessmentId,
            String correctionReason
    ) {
        return new PatientAssessmentRequest(
                AgeStatus.ESTIMATED,
                ageYears,
                Sex.MALE,
                OccurrenceType.NON_DISEASE,
                null,
                InjuryMechanism.FALL,
                null,
                Set.of(InjurySite.LOWER_LIMB),
                Symptom.TRAUMA,
                null,
                Set.of(Symptom.BLEEDING),
                TimeStatus.EXACT,
                now.minusSeconds(600),
                null,
                null,
                TimeStatus.EXACT,
                now.minusSeconds(600),
                null,
                null,
                now,
                supersedesAssessmentId,
                correctionReason
        );
    }

    private PreKtasAssessmentRequest preKtasAssessment() {
        return new PreKtasAssessmentRequest(
                PreKtasClassificationStatus.COMPLETED,
                2,
                null,
                null,
                now,
                "pre-ktas-2026",
                now,
                null,
                null
        );
    }

    private ConsciousnessAssessmentRequest consciousnessAssessment() {
        return new ConsciousnessAssessmentRequest(
                Avpu.A,
                null,
                null,
                now,
                now,
                null,
                null
        );
    }

    private VitalSignSetRequest vitalSignSet() {
        VitalSignSetRequest.IntegerVitalItem normalInteger = new VitalSignSetRequest.IntegerVitalItem(
                ClinicalValueState.VALUE,
                80,
                null,
                null
        );
        return new VitalSignSetRequest(
                new VitalSignSetRequest.BloodPressureItem(ClinicalValueState.VALUE, 120, 80, null, null),
                normalInteger,
                new VitalSignSetRequest.IntegerVitalItem(ClinicalValueState.VALUE, 18, null, null),
                new VitalSignSetRequest.TemperatureItem(ClinicalValueState.VALUE, BigDecimal.valueOf(36.5), null, null),
                new VitalSignSetRequest.IntegerVitalItem(ClinicalValueState.VALUE, 98, null, null),
                now,
                now,
                null,
                null
        );
    }

    private TreatmentEventRequest treatmentEvent() {
        return new TreatmentEventRequest(
                TreatmentType.OXYGEN,
                now,
                now,
                "initial-treatment-v1",
                "{\"method\":\"MASK\",\"flowLpm\":5}",
                null,
                null
        );
    }
}
