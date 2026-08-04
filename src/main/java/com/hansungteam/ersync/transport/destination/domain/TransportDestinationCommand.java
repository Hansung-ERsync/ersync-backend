package com.hansungteam.ersync.transport.destination.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
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

/** 목적지 선택·변경·무변경 명령의 멱등 결과를 보존하는 불변 이력입니다. */
@Entity
@Table(name = "transport_destination_commands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportDestinationCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_destination_offer_id")
    private HospitalOffer previousDestinationOffer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_offer_id", nullable = false)
    private HospitalOffer destinationOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 20)
    private TransportDestinationResultType resultType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", nullable = false)
    private UserAccount actorAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_organization_id", nullable = false)
    private Organization actorOrganization;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "binary(32)")
    private byte[] requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "resulting_request_status", nullable = false, length = 30)
    private TransportRequestStatus resultingRequestStatus;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant occurredAt;

    private TransportDestinationCommand(
            TransportRequest transportRequest,
            HospitalOffer previousDestinationOffer,
            HospitalOffer destinationOffer,
            TransportDestinationResultType resultType,
            UserAccount actorAccount,
            Organization actorOrganization,
            String idempotencyKey,
            byte[] requestFingerprint,
            Instant occurredAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.previousDestinationOffer = previousDestinationOffer;
        this.destinationOffer = destinationOffer;
        this.resultType = resultType;
        this.actorAccount = actorAccount;
        this.actorOrganization = actorOrganization;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = Arrays.copyOf(requestFingerprint, requestFingerprint.length);
        this.resultingRequestStatus = TransportRequestStatus.EN_ROUTE;
        this.occurredAt = occurredAt;
    }

    public static TransportDestinationCommand record(
            TransportRequest transportRequest,
            HospitalOffer previousDestinationOffer,
            HospitalOffer destinationOffer,
            TransportDestinationResultType resultType,
            UserAccount actorAccount,
            Organization actorOrganization,
            String idempotencyKey,
            byte[] requestFingerprint,
            Instant occurredAt
    ) {
        return new TransportDestinationCommand(
                transportRequest,
                previousDestinationOffer,
                destinationOffer,
                resultType,
                actorAccount,
                actorOrganization,
                idempotencyKey,
                requestFingerprint,
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
