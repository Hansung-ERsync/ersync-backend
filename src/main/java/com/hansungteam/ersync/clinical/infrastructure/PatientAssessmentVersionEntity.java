package com.hansungteam.ersync.clinical.infrastructure;

import com.hansungteam.ersync.clinical.api.PatientAssessmentRequest;
import com.hansungteam.ersync.clinical.domain.AgeStatus;
import com.hansungteam.ersync.clinical.domain.InjuryMechanism;
import com.hansungteam.ersync.clinical.domain.InjurySite;
import com.hansungteam.ersync.clinical.domain.OccurrenceType;
import com.hansungteam.ersync.clinical.domain.Sex;
import com.hansungteam.ersync.clinical.domain.Symptom;
import com.hansungteam.ersync.clinical.domain.TimeStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * 환자 기본·발생 정보의 append-only 버전 row입니다.
 */
@Entity
@Table(name = "patient_assessment_versions")
public class PatientAssessmentVersionEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String transportRequestId;

    @Column(nullable = false)
    private int versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgeStatus ageStatus;

    private Integer ageYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OccurrenceType occurrenceType;

    @Column(length = 120)
    private String occurrenceOtherDetail;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private InjuryMechanism mechanism;

    @Column(length = 120)
    private String mechanismOtherDetail;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "patient_assessment_injury_sites",
            joinColumns = @JoinColumn(name = "patient_assessment_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "injury_site", nullable = false, length = 40)
    private Set<InjurySite> injurySites;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private Symptom primarySymptom;

    @Column(length = 120)
    private String primarySymptomOtherDetail;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "patient_assessment_secondary_symptoms",
            joinColumns = @JoinColumn(name = "patient_assessment_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "symptom", nullable = false, length = 40)
    private Set<Symptom> secondarySymptoms;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TimeStatus onsetTimeStatus;

    private Instant onsetAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TimeStatus lastKnownWellStatus;

    private Instant lastKnownWellAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TimeStatus accidentTimeStatus;

    private Instant accidentAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TimeStatus cardiacArrestTimeStatus;

    private Instant cardiacArrestAt;

    @Column(nullable = false)
    private Instant enteredAt;

    @Column(nullable = false)
    private Instant serverReceivedAt;

    @Column(nullable = false, length = 36)
    private String createdBy;

    @Column(length = 36)
    private String supersedesAssessmentId;

    @Column(length = 255)
    private String correctionReason;

    protected PatientAssessmentVersionEntity() {
    }

    public PatientAssessmentVersionEntity(
            String transportRequestId,
            int versionNumber,
            String createdBy,
            Instant serverReceivedAt,
            PatientAssessmentRequest request
    ) {
        this.id = UUID.randomUUID().toString();
        this.transportRequestId = transportRequestId;
        this.versionNumber = versionNumber;
        this.createdBy = createdBy;
        this.serverReceivedAt = serverReceivedAt;
        this.ageStatus = request.ageStatus();
        this.ageYears = request.ageYears();
        this.sex = request.sex();
        this.occurrenceType = request.occurrenceType();
        this.occurrenceOtherDetail = request.occurrenceOtherDetail();
        this.mechanism = request.mechanism();
        this.mechanismOtherDetail = request.mechanismOtherDetail();
        this.injurySites = request.injurySites() == null ? Set.of() : Set.copyOf(request.injurySites());
        this.primarySymptom = request.primarySymptom();
        this.primarySymptomOtherDetail = request.primarySymptomOtherDetail();
        this.secondarySymptoms = request.secondarySymptoms() == null ? Set.of() : Set.copyOf(request.secondarySymptoms());
        this.onsetTimeStatus = request.onsetTimeStatus();
        this.onsetAt = request.onsetAt();
        this.lastKnownWellStatus = request.lastKnownWellStatus();
        this.lastKnownWellAt = request.lastKnownWellAt();
        this.accidentTimeStatus = request.accidentTimeStatus();
        this.accidentAt = request.accidentAt();
        this.cardiacArrestTimeStatus = request.cardiacArrestTimeStatus();
        this.cardiacArrestAt = request.cardiacArrestAt();
        this.enteredAt = request.enteredAt();
        this.supersedesAssessmentId = request.supersedesAssessmentId();
        this.correctionReason = request.correctionReason();
    }

    public String id() {
        return id;
    }

    public String transportRequestId() {
        return transportRequestId;
    }

    public int versionNumber() {
        return versionNumber;
    }

    public AgeStatus ageStatus() {
        return ageStatus;
    }

    public Integer ageYears() {
        return ageYears;
    }

    public String supersedesAssessmentId() {
        return supersedesAssessmentId;
    }
}
