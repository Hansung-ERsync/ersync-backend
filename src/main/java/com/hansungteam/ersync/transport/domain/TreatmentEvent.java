package com.hansungteam.ersync.transport.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 성공·실패 여부와 관계없이 보존되는 append-only 처치 기록입니다. */
@Entity
@Table(name = "treatment_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TreatmentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "treatment_type", nullable = false, length = 40)
    private TreatmentType treatmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "attempt_result", length = 30)
    private TreatmentAttemptResult attemptResult;

    @Embedded
    private TreatmentDetails details;

    @Column(name = "performed_at", columnDefinition = "datetime(6)")
    private Instant performedAt;

    @Column(name = "entered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enteredAt;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private UserAccount createdBy;

    private TreatmentEvent(
            TransportRequest transportRequest,
            TreatmentType treatmentType,
            TreatmentAttemptResult attemptResult,
            TreatmentDetails details,
            Instant performedAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.treatmentType = treatmentType;
        this.attemptResult = attemptResult;
        this.details = details;
        this.performedAt = performedAt;
        this.enteredAt = enteredAt;
        this.serverReceivedAt = serverReceivedAt;
        this.createdBy = createdBy;
    }

    public static TreatmentEvent create(
            TransportRequest transportRequest,
            TreatmentType treatmentType,
            TreatmentAttemptResult attemptResult,
            TreatmentDetails details,
            Instant performedAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        return new TreatmentEvent(
                transportRequest,
                treatmentType,
                attemptResult,
                details,
                performedAt,
                enteredAt,
                serverReceivedAt,
                createdBy
        );
    }
}
