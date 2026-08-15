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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 최초 환자 평가와 후속 병원 탐색 흐름을 묶는 이송 요청입니다. */
@Entity
@Table(name = "transport_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private UserAccount ownerAccount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransportRequestStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_destination_offer_id")
    private HospitalOffer currentDestinationOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancellation_reason", length = 40)
    private TransportCancellationReason cancellationReason;

    @Column(name = "cancellation_detail", length = 200)
    private String cancellationDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_account_id")
    private UserAccount cancelledByAccount;

    @Column(name = "cancelled_at", columnDefinition = "datetime(6)")
    private Instant cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handoff_requested_by_account_id")
    private UserAccount handoffRequestedByAccount;

    @Column(name = "handoff_requested_at", columnDefinition = "datetime(6)")
    private Instant handoffRequestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handoff_confirmed_by_account_id")
    private UserAccount handoffConfirmedByAccount;

    @Column(name = "completed_at", columnDefinition = "datetime(6)")
    private Instant completedAt;

    @Column(name = "callback_contact", nullable = false, length = 30)
    private String callbackContact;

    @Column(name = "assessment_protocol_version", nullable = false, length = 50)
    private String assessmentProtocolVersion;

    @Column(name = "origin_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLatitude;

    @Column(name = "origin_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_source", nullable = false, length = 30)
    private OriginSource originSource;

    @Column(name = "client_idempotency_key", nullable = false, length = 100)
    private String clientIdempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "binary(32)")
    private byte[] requestFingerprint;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private TransportRequest(
            UserAccount ownerAccount,
            Organization organization,
            String callbackContact,
            String assessmentProtocolVersion,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            OriginSource originSource,
            String clientIdempotencyKey,
            byte[] requestFingerprint,
            Instant serverReceivedAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.ownerAccount = ownerAccount;
        this.organization = organization;
        this.status = TransportRequestStatus.SEARCHING;
        this.callbackContact = callbackContact;
        this.assessmentProtocolVersion = assessmentProtocolVersion;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.originSource = originSource;
        this.clientIdempotencyKey = clientIdempotencyKey;
        this.requestFingerprint = Arrays.copyOf(requestFingerprint, requestFingerprint.length);
        this.serverReceivedAt = serverReceivedAt;
        this.createdAt = serverReceivedAt;
        this.updatedAt = serverReceivedAt;
    }

    /** 인증된 구급대원과 요청 당시 연락처를 고정한 최초 이송 요청을 생성합니다. */
    public static TransportRequest create(
            UserAccount ownerAccount,
            Organization organization,
            String callbackContact,
            String assessmentProtocolVersion,
            BigDecimal originLatitude,
            BigDecimal originLongitude,
            OriginSource originSource,
            String clientIdempotencyKey,
            byte[] requestFingerprint,
            Instant serverReceivedAt
    ) {
        return new TransportRequest(
                ownerAccount,
                organization,
                callbackContact,
                assessmentProtocolVersion,
                originLatitude,
                originLongitude,
                originSource,
                clientIdempotencyKey,
                requestFingerprint,
                serverReceivedAt
        );
    }

    public boolean hasSameFingerprint(byte[] fingerprint) {
        return Arrays.equals(requestFingerprint, fingerprint);
    }

    /** 탐색 중이거나 후보 소진 뒤 병원이 수락하면 목적지를 선택할 수 있는 상태로 변경합니다. */
    public void markAcceptedAvailable() {
        if (status == TransportRequestStatus.SEARCHING
                || status == TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            status = TransportRequestStatus.ACCEPTED_AVAILABLE;
        }
    }

    /** 수락 병원을 현재 목적지로 원자적으로 지정하고 이동 중 상태로 전환합니다. */
    public void selectDestination(HospitalOffer destinationOffer) {
        if (status != TransportRequestStatus.ACCEPTED_AVAILABLE
                && status != TransportRequestStatus.EN_ROUTE) {
            throw new IllegalStateException("Destination cannot be selected in the current request status");
        }
        currentDestinationOffer = destinationOffer;
        status = TransportRequestStatus.EN_ROUTE;
    }

    /** 현재 목적지 철회 뒤 남은 수락 여부에 따라 요청을 다시 선택·탐색 상태로 전환합니다. */
    public void clearDestinationAfterWithdrawal(boolean hasRemainingAcceptedOffer) {
        currentDestinationOffer = null;
        status = hasRemainingAcceptedOffer
                ? TransportRequestStatus.ACCEPTED_AVAILABLE
                : TransportRequestStatus.SEARCHING;
    }

    /** 목적지가 아직 없던 수락 철회 뒤에도 남은 수락 여부와 요청 상태를 맞춥니다. */
    public void transitionAfterDestinationFreeWithdrawal(boolean hasRemainingAcceptedOffer) {
        if (currentDestinationOffer != null) {
            throw new IllegalStateException("A request with a destination cannot use destination-free withdrawal");
        }
        status = hasRemainingAcceptedOffer
                ? TransportRequestStatus.ACCEPTED_AVAILABLE
                : TransportRequestStatus.SEARCHING;
    }

    public boolean hasDestination(HospitalOffer offer) {
        return currentDestinationOffer != null && currentDestinationOffer.getId().equals(offer.getId());
    }

    /** 활성 요청을 사유와 함께 종료하고 현재 목적지를 활성 연결에서 해제합니다. */
    public void cancel(
            UserAccount actor,
            TransportCancellationReason reason,
            String detail,
            Instant occurredAt
    ) {
        if (status != TransportRequestStatus.SEARCHING
                && status != TransportRequestStatus.CANDIDATES_EXHAUSTED
                && status != TransportRequestStatus.ACCEPTED_AVAILABLE
                && status != TransportRequestStatus.EN_ROUTE) {
            throw new IllegalStateException("Transport cannot be cancelled in the current status");
        }
        cancellationReason = reason;
        cancellationDetail = detail;
        cancelledByAccount = actor;
        cancelledAt = occurredAt;
        currentDestinationOffer = null;
        status = TransportRequestStatus.CANCELLED;
    }

    /** 구급대원이 목적지 병원에 실제 인계 확인을 요청합니다. */
    public void requestHandoff(UserAccount actor, Instant occurredAt) {
        if (status != TransportRequestStatus.EN_ROUTE || currentDestinationOffer == null) {
            throw new IllegalStateException("Handoff cannot be requested without an active destination");
        }
        handoffRequestedByAccount = actor;
        handoffRequestedAt = occurredAt;
        status = TransportRequestStatus.HANDOFF_REQUESTED;
    }

    /** 현재 목적지 병원이 인수를 확인한 뒤 이송을 최종 완료합니다. */
    public void confirmHandoff(UserAccount actor, Instant occurredAt) {
        if (status != TransportRequestStatus.HANDOFF_REQUESTED || currentDestinationOffer == null) {
            throw new IllegalStateException("Handoff cannot be confirmed in the current status");
        }
        handoffConfirmedByAccount = actor;
        completedAt = occurredAt;
        status = TransportRequestStatus.COMPLETED;
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
