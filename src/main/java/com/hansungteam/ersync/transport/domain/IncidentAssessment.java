package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** 최초 발생 유형, 증상과 발생 시각을 구조화한 기록입니다. */
@Entity
@Table(name = "incident_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IncidentAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false, unique = true)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "occurrence_type", nullable = false, length = 30)
    private OccurrenceType occurrenceType;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private InjuryMechanism mechanism;

    @Column(name = "occurrence_detail", length = 200)
    private String occurrenceDetail;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "incident_injury_sites",
            joinColumns = @JoinColumn(name = "incident_assessment_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "injury_site", nullable = false, length = 30)
    private Set<InjurySite> injurySites = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_symptom", nullable = false, length = 40)
    private Symptom primarySymptom;

    @Column(name = "primary_symptom_detail", length = 200)
    private String primarySymptomDetail;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "incident_secondary_symptoms",
            joinColumns = @JoinColumn(name = "incident_assessment_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "symptom", nullable = false, length = 40)
    private Set<Symptom> secondarySymptoms = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "onset_time_status", nullable = false, length = 20)
    private OnsetTimeStatus onsetTimeStatus;

    @Column(name = "onset_at", columnDefinition = "datetime(6)")
    private Instant onsetAt;

    @Column(name = "entered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enteredAt;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    private IncidentAssessment(
            TransportRequest transportRequest,
            OccurrenceType occurrenceType,
            InjuryMechanism mechanism,
            String occurrenceDetail,
            Set<InjurySite> injurySites,
            Symptom primarySymptom,
            String primarySymptomDetail,
            Set<Symptom> secondarySymptoms,
            OnsetTimeStatus onsetTimeStatus,
            Instant onsetAt,
            Instant enteredAt,
            Instant serverReceivedAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.occurrenceType = occurrenceType;
        this.mechanism = mechanism;
        this.occurrenceDetail = occurrenceDetail;
        this.injurySites.addAll(injurySites);
        this.primarySymptom = primarySymptom;
        this.primarySymptomDetail = primarySymptomDetail;
        this.secondarySymptoms.addAll(secondarySymptoms);
        this.onsetTimeStatus = onsetTimeStatus;
        this.onsetAt = onsetAt;
        this.enteredAt = enteredAt;
        this.serverReceivedAt = serverReceivedAt;
    }

    public static IncidentAssessment create(
            TransportRequest transportRequest,
            OccurrenceType occurrenceType,
            InjuryMechanism mechanism,
            String occurrenceDetail,
            Set<InjurySite> injurySites,
            Symptom primarySymptom,
            String primarySymptomDetail,
            Set<Symptom> secondarySymptoms,
            OnsetTimeStatus onsetTimeStatus,
            Instant onsetAt,
            Instant enteredAt,
            Instant serverReceivedAt
    ) {
        return new IncidentAssessment(
                transportRequest,
                occurrenceType,
                mechanism,
                occurrenceDetail,
                injurySites,
                primarySymptom,
                primarySymptomDetail,
                secondarySymptoms,
                onsetTimeStatus,
                onsetAt,
                enteredAt,
                serverReceivedAt
        );
    }
}
