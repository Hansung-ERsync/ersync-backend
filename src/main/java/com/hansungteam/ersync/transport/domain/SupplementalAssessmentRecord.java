package com.hansungteam.ersync.transport.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 타입별 상세와 공통 버전·시각을 분리해 보존하는 append-only 추가 평가 원본입니다. */
@Entity
@Table(name = "supplemental_assessment_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplementalAssessmentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false, length = 40)
    private SupplementalAssessmentType assessmentType;

    @Column(name = "assessment_protocol_version", nullable = false, length = 50)
    private String assessmentProtocolVersion;

    @Column(name = "assessed_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant assessedAt;

    @Column(name = "entered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enteredAt;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private UserAccount createdBy;

    @OneToOne(mappedBy = "supplementalAssessment", fetch = FetchType.LAZY)
    private GeneralSupplementalAssessment generalAssessment;

    private SupplementalAssessmentRecord(
            TransportRequest transportRequest,
            String assessmentProtocolVersion,
            Instant assessedAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.assessmentType = SupplementalAssessmentType.GENERAL;
        this.assessmentProtocolVersion = assessmentProtocolVersion;
        this.assessedAt = assessedAt;
        this.enteredAt = enteredAt;
        this.serverReceivedAt = serverReceivedAt;
        this.createdBy = createdBy;
    }

    public static SupplementalAssessmentRecord createGeneral(
            TransportRequest transportRequest,
            String assessmentProtocolVersion,
            Instant assessedAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        return new SupplementalAssessmentRecord(
                transportRequest,
                assessmentProtocolVersion,
                assessedAt,
                enteredAt,
                serverReceivedAt,
                createdBy
        );
    }

    public void attachGeneralAssessment(GeneralSupplementalAssessment assessment) {
        if (assessmentType != SupplementalAssessmentType.GENERAL || generalAssessment != null) {
            throw new IllegalStateException("GENERAL supplemental assessment is already attached");
        }
        this.generalAssessment = assessment;
    }
}
