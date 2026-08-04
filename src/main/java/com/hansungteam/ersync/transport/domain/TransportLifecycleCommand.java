package com.hansungteam.ersync.transport.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.organization.domain.Organization;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 취소·인계 요청·인계 확인의 결과와 행위자를 보존하는 불변 명령 이력입니다. */
@Entity
@Table(name = "transport_lifecycle_commands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportLifecycleCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 30)
    private TransportLifecycleCommandType commandType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", nullable = false)
    private UserAccount actorAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_organization_id", nullable = false)
    private Organization actorOrganization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_offer_id")
    private HospitalOffer destinationOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 40)
    private TransportCancellationReason cancellationReason;

    @Column(name = "cancellation_detail", length = 200)
    private String cancellationDetail;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "binary(32)")
    private byte[] requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_request_status", nullable = false, length = 30)
    private TransportRequestStatus resultingRequestStatus;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant occurredAt;

    private TransportLifecycleCommand(
            TransportRequest transportRequest,
            TransportLifecycleCommandType commandType,
            UserAccount actorAccount,
            Organization actorOrganization,
            HospitalOffer destinationOffer,
            TransportCancellationReason cancellationReason,
            String cancellationDetail,
            String idempotencyKey,
            byte[] requestFingerprint,
            TransportRequestStatus resultingRequestStatus,
            Instant occurredAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.commandType = commandType;
        this.actorAccount = actorAccount;
        this.actorOrganization = actorOrganization;
        this.destinationOffer = destinationOffer;
        this.cancellationReason = cancellationReason;
        this.cancellationDetail = cancellationDetail;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = Arrays.copyOf(requestFingerprint, requestFingerprint.length);
        this.resultingRequestStatus = resultingRequestStatus;
        this.occurredAt = occurredAt;
    }

    public static TransportLifecycleCommand cancel(
            TransportRequest transportRequest,
            UserAccount actorAccount,
            HospitalOffer previousDestination,
            TransportCancellationReason reason,
            String detail,
            String idempotencyKey,
            byte[] fingerprint,
            Instant occurredAt
    ) {
        return new TransportLifecycleCommand(
                transportRequest,
                TransportLifecycleCommandType.CANCEL,
                actorAccount,
                actorAccount.getOrganization(),
                previousDestination,
                reason,
                detail,
                idempotencyKey,
                fingerprint,
                TransportRequestStatus.CANCELLED,
                occurredAt
        );
    }

    public static TransportLifecycleCommand handoffRequest(
            TransportRequest transportRequest,
            UserAccount actorAccount,
            HospitalOffer destination,
            String idempotencyKey,
            byte[] fingerprint,
            Instant occurredAt
    ) {
        return handoff(
                transportRequest,
                TransportLifecycleCommandType.HANDOFF_REQUEST,
                actorAccount,
                destination,
                idempotencyKey,
                fingerprint,
                TransportRequestStatus.HANDOFF_REQUESTED,
                occurredAt
        );
    }

    public static TransportLifecycleCommand handoffConfirm(
            TransportRequest transportRequest,
            UserAccount actorAccount,
            HospitalOffer destination,
            String idempotencyKey,
            byte[] fingerprint,
            Instant occurredAt
    ) {
        return handoff(
                transportRequest,
                TransportLifecycleCommandType.HANDOFF_CONFIRM,
                actorAccount,
                destination,
                idempotencyKey,
                fingerprint,
                TransportRequestStatus.COMPLETED,
                occurredAt
        );
    }

    private static TransportLifecycleCommand handoff(
            TransportRequest transportRequest,
            TransportLifecycleCommandType commandType,
            UserAccount actorAccount,
            HospitalOffer destination,
            String idempotencyKey,
            byte[] fingerprint,
            TransportRequestStatus resultingStatus,
            Instant occurredAt
    ) {
        return new TransportLifecycleCommand(
                transportRequest,
                commandType,
                actorAccount,
                actorAccount.getOrganization(),
                destination,
                null,
                null,
                idempotencyKey,
                fingerprint,
                resultingStatus,
                occurredAt
        );
    }

    public boolean hasSameFingerprint(byte[] fingerprint) {
        return Arrays.equals(requestFingerprint, fingerprint);
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
