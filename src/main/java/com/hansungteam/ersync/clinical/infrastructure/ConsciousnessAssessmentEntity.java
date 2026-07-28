package com.hansungteam.ersync.clinical.infrastructure;

import com.hansungteam.ersync.clinical.api.ConsciousnessAssessmentRequest;
import com.hansungteam.ersync.clinical.domain.Avpu;
import com.hansungteam.ersync.clinical.domain.ConsciousnessUnassessableReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * AVPU 의식 평가의 append-only row입니다.
 */
@Entity
@Table(name = "consciousness_assessments")
public class ConsciousnessAssessmentEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String transportRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Avpu avpu;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private ConsciousnessUnassessableReason unassessableReason;

    @Column(length = 120)
    private String unassessableDetail;

    @Column(nullable = false)
    private Instant observedAt;

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

    protected ConsciousnessAssessmentEntity() {
    }

    public ConsciousnessAssessmentEntity(
            String transportRequestId,
            String createdBy,
            Instant serverReceivedAt,
            ConsciousnessAssessmentRequest request
    ) {
        this.id = UUID.randomUUID().toString();
        this.transportRequestId = transportRequestId;
        this.createdBy = createdBy;
        this.serverReceivedAt = serverReceivedAt;
        this.avpu = request.avpu();
        this.unassessableReason = request.unassessableReason();
        this.unassessableDetail = request.unassessableDetail();
        this.observedAt = request.observedAt();
        this.enteredAt = request.enteredAt();
        this.supersedesAssessmentId = request.supersedesAssessmentId();
        this.correctionReason = request.correctionReason();
    }
}
