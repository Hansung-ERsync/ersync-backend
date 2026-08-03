package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.Column;
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
import java.util.UUID;

/** 직접 식별정보 없이 나이 상태와 성별만 보관하는 최초 환자 기본정보입니다. */
@Entity
@Table(name = "patient_demographics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientDemographics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false, unique = true)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_status", nullable = false, length = 20)
    private AgeStatus ageStatus;

    @Column(name = "age_years")
    private Integer ageYears;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PatientSex sex;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    private PatientDemographics(
            TransportRequest transportRequest,
            AgeStatus ageStatus,
            Integer ageYears,
            PatientSex sex,
            Instant createdAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.ageStatus = ageStatus;
        this.ageYears = ageYears;
        this.sex = sex;
        this.createdAt = createdAt;
    }

    public static PatientDemographics create(
            TransportRequest transportRequest,
            AgeStatus ageStatus,
            Integer ageYears,
            PatientSex sex,
            Instant createdAt
    ) {
        return new PatientDemographics(transportRequest, ageStatus, ageYears, sex, createdAt);
    }
}
