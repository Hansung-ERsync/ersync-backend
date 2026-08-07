package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 현재 Flutter 입력 화면의 여섯 추가 평가 종류를 구조화해 보존합니다. */
@Entity
@Table(name = "general_supplemental_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GeneralSupplementalAssessment {

    @Id
    @Column(name = "supplemental_assessment_id")
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplemental_assessment_id", nullable = false)
    private SupplementalAssessmentRecord supplementalAssessment;

    @Column(name = "glucose_mg_dl")
    private Integer glucoseMgDl;

    @Enumerated(EnumType.STRING)
    @Column(name = "left_pupil", length = 30)
    private PupilResponse leftPupil;

    @Enumerated(EnumType.STRING)
    @Column(name = "right_pupil", length = 30)
    private PupilResponse rightPupil;

    @Column(name = "medical_history", length = 120)
    private String medicalHistory;

    @Column(length = 120)
    private String allergies;

    @Column(length = 120)
    private String medications;

    @Column(name = "isolation_concern")
    private Boolean isolationConcern;

    private GeneralSupplementalAssessment(
            SupplementalAssessmentRecord supplementalAssessment,
            Integer glucoseMgDl,
            PupilResponse leftPupil,
            PupilResponse rightPupil,
            String medicalHistory,
            String allergies,
            String medications,
            Boolean isolationConcern
    ) {
        this.supplementalAssessment = supplementalAssessment;
        this.glucoseMgDl = glucoseMgDl;
        this.leftPupil = leftPupil;
        this.rightPupil = rightPupil;
        this.medicalHistory = medicalHistory;
        this.allergies = allergies;
        this.medications = medications;
        this.isolationConcern = isolationConcern;
        supplementalAssessment.attachGeneralAssessment(this);
    }

    public static GeneralSupplementalAssessment create(
            SupplementalAssessmentRecord supplementalAssessment,
            Integer glucoseMgDl,
            PupilResponse leftPupil,
            PupilResponse rightPupil,
            String medicalHistory,
            String allergies,
            String medications,
            Boolean isolationConcern
    ) {
        return new GeneralSupplementalAssessment(
                supplementalAssessment,
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
