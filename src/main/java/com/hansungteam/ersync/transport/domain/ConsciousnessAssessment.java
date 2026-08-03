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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** append-only AVPU 의식 평가 기록입니다. */
@Entity
@Table(name = "consciousness_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsciousnessAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Avpu avpu;

    @Enumerated(EnumType.STRING)
    @Column(name = "unassessable_reason", length = 30)
    private ConsciousnessUnassessableReason unassessableReason;

    @Column(name = "unassessable_detail", length = 200)
    private String unassessableDetail;

    @Column(name = "observed_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant observedAt;

    @Column(name = "entered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enteredAt;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private UserAccount createdBy;

    private ConsciousnessAssessment(
            TransportRequest transportRequest,
            Avpu avpu,
            ConsciousnessUnassessableReason unassessableReason,
            String unassessableDetail,
            Instant observedAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.avpu = avpu;
        this.unassessableReason = unassessableReason;
        this.unassessableDetail = unassessableDetail;
        this.observedAt = observedAt;
        this.enteredAt = enteredAt;
        this.serverReceivedAt = serverReceivedAt;
        this.createdBy = createdBy;
    }

    public static ConsciousnessAssessment create(
            TransportRequest transportRequest,
            Avpu avpu,
            ConsciousnessUnassessableReason unassessableReason,
            String unassessableDetail,
            Instant observedAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        return new ConsciousnessAssessment(
                transportRequest,
                avpu,
                unassessableReason,
                unassessableDetail,
                observedAt,
                enteredAt,
                serverReceivedAt,
                createdBy
        );
    }
}
