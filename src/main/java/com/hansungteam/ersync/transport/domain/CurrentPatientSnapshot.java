package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 임상 원본을 보존하면서 최신 기록을 가리키는 파생 읽기 모델입니다. */
@Entity
@Table(name = "current_patient_snapshots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurrentPatientSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false, unique = true)
    private TransportRequest transportRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_demographics_id", nullable = false)
    private PatientDemographics patientDemographics;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_assessment_id", nullable = false)
    private IncidentAssessment incidentAssessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_pre_ktas_assessment_id", nullable = false)
    private PreKtasAssessment latestPreKtasAssessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_consciousness_assessment_id", nullable = false)
    private ConsciousnessAssessment latestConsciousnessAssessment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "latest_vital_sign_set_id", nullable = false)
    private VitalSignSet latestVitalSignSet;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "current_patient_snapshot_treatments",
            joinColumns = @JoinColumn(name = "snapshot_id"),
            inverseJoinColumns = @JoinColumn(name = "treatment_event_id")
    )
    @OrderBy("serverReceivedAt ASC, id ASC")
    private List<TreatmentEvent> currentTreatments = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_supplemental_assessment_id")
    private SupplementalAssessmentRecord latestSupplementalAssessment;

    @Column(name = "assessment_protocol_version", nullable = false, length = 50)
    private String assessmentProtocolVersion;

    @Column(name = "last_clinical_update_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant lastClinicalUpdateAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private CurrentPatientSnapshot(
            TransportRequest transportRequest,
            PatientDemographics patientDemographics,
            IncidentAssessment incidentAssessment,
            PreKtasAssessment latestPreKtasAssessment,
            ConsciousnessAssessment latestConsciousnessAssessment,
            VitalSignSet latestVitalSignSet,
            List<TreatmentEvent> currentTreatments,
            SupplementalAssessmentRecord latestSupplementalAssessment,
            String assessmentProtocolVersion,
            Instant lastClinicalUpdateAt,
            Instant createdAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.patientDemographics = patientDemographics;
        this.incidentAssessment = incidentAssessment;
        this.latestPreKtasAssessment = latestPreKtasAssessment;
        this.latestConsciousnessAssessment = latestConsciousnessAssessment;
        this.latestVitalSignSet = latestVitalSignSet;
        this.currentTreatments.addAll(currentTreatments);
        this.latestSupplementalAssessment = latestSupplementalAssessment;
        this.assessmentProtocolVersion = assessmentProtocolVersion;
        this.lastClinicalUpdateAt = lastClinicalUpdateAt;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** 최초 임상 기록을 최신값으로 가리키는 요청별 snapshot을 생성합니다. */
    public static CurrentPatientSnapshot create(
            TransportRequest transportRequest,
            PatientDemographics patientDemographics,
            IncidentAssessment incidentAssessment,
            PreKtasAssessment latestPreKtasAssessment,
            ConsciousnessAssessment latestConsciousnessAssessment,
            VitalSignSet latestVitalSignSet,
            List<TreatmentEvent> currentTreatments,
            SupplementalAssessmentRecord latestSupplementalAssessment,
            String assessmentProtocolVersion,
            Instant lastClinicalUpdateAt,
            Instant createdAt
    ) {
        return new CurrentPatientSnapshot(
                transportRequest,
                patientDemographics,
                incidentAssessment,
                latestPreKtasAssessment,
                latestConsciousnessAssessment,
                latestVitalSignSet,
                currentTreatments,
                latestSupplementalAssessment,
                assessmentProtocolVersion,
                lastClinicalUpdateAt,
                createdAt
        );
    }

    /** 추가 평가 필드가 생기기 전 내부 호출과 도메인 테스트의 호환 생성자입니다. */
    public static CurrentPatientSnapshot create(
            TransportRequest transportRequest,
            PatientDemographics patientDemographics,
            IncidentAssessment incidentAssessment,
            PreKtasAssessment latestPreKtasAssessment,
            ConsciousnessAssessment latestConsciousnessAssessment,
            VitalSignSet latestVitalSignSet,
            List<TreatmentEvent> currentTreatments,
            String assessmentProtocolVersion,
            Instant lastClinicalUpdateAt,
            Instant createdAt
    ) {
        return create(
                transportRequest,
                patientDemographics,
                incidentAssessment,
                latestPreKtasAssessment,
                latestConsciousnessAssessment,
                latestVitalSignSet,
                currentTreatments,
                null,
                assessmentProtocolVersion,
                lastClinicalUpdateAt,
                createdAt
        );
    }

    /** 임상 시각과 서버 수신 시각이 더 최신일 때만 활력징후 포인터를 전진시킵니다. */
    public boolean advanceVitalSigns(VitalSignSet candidate) {
        if (!isLater(
                candidate.getMeasuredAt(),
                candidate.getServerReceivedAt(),
                latestVitalSignSet.getMeasuredAt(),
                latestVitalSignSet.getServerReceivedAt()
        )) {
            touch(candidate.getServerReceivedAt());
            return false;
        }
        latestVitalSignSet = candidate;
        touch(candidate.getServerReceivedAt());
        return true;
    }

    /** 임상 시각과 서버 수신 시각이 더 최신일 때만 의식 평가 포인터를 전진시킵니다. */
    public boolean advanceConsciousness(ConsciousnessAssessment candidate) {
        if (!isLater(
                candidate.getObservedAt(),
                candidate.getServerReceivedAt(),
                latestConsciousnessAssessment.getObservedAt(),
                latestConsciousnessAssessment.getServerReceivedAt()
        )) {
            touch(candidate.getServerReceivedAt());
            return false;
        }
        latestConsciousnessAssessment = candidate;
        touch(candidate.getServerReceivedAt());
        return true;
    }

    /** 완료·긴급 미완료를 같은 임상 시각 규칙으로 비교해 Pre-KTAS 포인터를 전진시킵니다. */
    public boolean advancePreKtas(PreKtasAssessment candidate) {
        Instant candidateClinicalAt = clinicalAt(candidate);
        Instant currentClinicalAt = clinicalAt(latestPreKtasAssessment);
        if (!isLater(
                candidateClinicalAt,
                candidate.getServerReceivedAt(),
                currentClinicalAt,
                latestPreKtasAssessment.getServerReceivedAt()
        )) {
            touch(candidate.getServerReceivedAt());
            return false;
        }
        latestPreKtasAssessment = candidate;
        touch(candidate.getServerReceivedAt());
        return true;
    }

    /** 최초 NONE은 원본에 남기되 실제 처치가 생기면 현재 처치 목록에서는 제거합니다. */
    public void appendTreatment(TreatmentEvent treatment) {
        currentTreatments.removeIf(current -> current.getTreatmentType() == TreatmentType.NONE);
        currentTreatments.add(treatment);
        touch(treatment.getServerReceivedAt());
    }

    private Instant clinicalAt(PreKtasAssessment assessment) {
        return assessment.getAssessedAt() == null ? assessment.getEnteredAt() : assessment.getAssessedAt();
    }

    private boolean isLater(
            Instant candidateClinicalAt,
            Instant candidateReceivedAt,
            Instant currentClinicalAt,
            Instant currentReceivedAt
    ) {
        int clinicalComparison = candidateClinicalAt.compareTo(currentClinicalAt);
        return clinicalComparison > 0
                || (clinicalComparison == 0 && !candidateReceivedAt.isBefore(currentReceivedAt));
    }

    private void touch(Instant receivedAt) {
        if (receivedAt.isAfter(lastClinicalUpdateAt)) {
            lastClinicalUpdateAt = receivedAt;
        }
        updatedAt = receivedAt;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
