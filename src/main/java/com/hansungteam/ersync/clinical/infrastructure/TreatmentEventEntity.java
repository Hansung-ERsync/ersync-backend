package com.hansungteam.ersync.clinical.infrastructure;

import com.hansungteam.ersync.clinical.api.TreatmentEventRequest;
import com.hansungteam.ersync.clinical.domain.TreatmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * 처치 또는 처치 시도 append-only row입니다.
 */
@Entity
@Table(name = "treatment_events")
public class TreatmentEventEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String transportRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private TreatmentType type;

    @Column(nullable = false)
    private Instant performedAt;

    @Column(nullable = false)
    private Instant enteredAt;

    @Column(nullable = false)
    private Instant serverReceivedAt;

    @Column(nullable = false, length = 36)
    private String createdBy;

    @Column(nullable = false, length = 40)
    private String detailSchemaVersion;

    @Column(columnDefinition = "json")
    private String detailsJson;

    @Column(length = 36)
    private String supersedesTreatmentEventId;

    @Column(length = 255)
    private String correctionReason;

    protected TreatmentEventEntity() {
    }

    public TreatmentEventEntity(
            String transportRequestId,
            String createdBy,
            Instant serverReceivedAt,
            TreatmentEventRequest request
    ) {
        this.id = UUID.randomUUID().toString();
        this.transportRequestId = transportRequestId;
        this.createdBy = createdBy;
        this.serverReceivedAt = serverReceivedAt;
        this.type = request.type();
        this.performedAt = request.performedAt();
        this.enteredAt = request.enteredAt();
        this.detailSchemaVersion = request.detailSchemaVersion();
        this.detailsJson = request.detailsJson();
        this.supersedesTreatmentEventId = request.supersedesTreatmentEventId();
        this.correctionReason = request.correctionReason();
    }

    public String id() {
        return id;
    }
}
