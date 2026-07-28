package com.hansungteam.ersync.clinical.infrastructure;

import com.hansungteam.ersync.clinical.api.PreKtasAssessmentRequest;
import com.hansungteam.ersync.clinical.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.clinical.domain.PreKtasExceptionReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Pre-KTAS 평가 또는 긴급 미완료 기록의 append-only row입니다.
 */
@Entity
@Table(name = "pre_ktas_assessments")
public class PreKtasAssessmentEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String transportRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PreKtasClassificationStatus classificationStatus;

    private Integer level;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private PreKtasExceptionReason exceptionReason;

    @Column(length = 120)
    private String exceptionDetail;

    @Column(nullable = false, length = 36)
    private String assessorAccountId;

    @Column(nullable = false)
    private Instant assessedAt;

    @Column(nullable = false, length = 80)
    private String standardVersion;

    @Column(nullable = false)
    private Instant enteredAt;

    @Column(nullable = false)
    private Instant serverReceivedAt;

    @Column(length = 36)
    private String supersedesAssessmentId;

    @Column(length = 255)
    private String correctionReason;

    protected PreKtasAssessmentEntity() {
    }

    public PreKtasAssessmentEntity(
            String transportRequestId,
            String assessorAccountId,
            Instant serverReceivedAt,
            PreKtasAssessmentRequest request
    ) {
        this.id = UUID.randomUUID().toString();
        this.transportRequestId = transportRequestId;
        this.assessorAccountId = assessorAccountId;
        this.serverReceivedAt = serverReceivedAt;
        this.classificationStatus = request.classificationStatus();
        this.level = request.level();
        this.exceptionReason = request.exceptionReason();
        this.exceptionDetail = request.exceptionDetail();
        this.assessedAt = request.assessedAt();
        this.standardVersion = request.standardVersion();
        this.enteredAt = request.enteredAt();
        this.supersedesAssessmentId = request.supersedesAssessmentId();
        this.correctionReason = request.correctionReason();
    }

    public String id() {
        return id;
    }

    public Integer level() {
        return level;
    }
}
