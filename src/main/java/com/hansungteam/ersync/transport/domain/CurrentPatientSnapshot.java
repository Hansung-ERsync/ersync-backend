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
                assessmentProtocolVersion,
                lastClinicalUpdateAt,
                createdAt
        );
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
