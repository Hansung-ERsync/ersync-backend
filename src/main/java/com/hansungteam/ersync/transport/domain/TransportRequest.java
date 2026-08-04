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

    /** 최대 탐색 반경에서 수락 병원이 없음을 기록합니다. */
    public void markCandidatesExhausted() {
        if (status != TransportRequestStatus.SEARCHING) {
            throw new IllegalStateException("Only a searching request can be exhausted");
        }
        status = TransportRequestStatus.CANDIDATES_EXHAUSTED;
    }

    /** 탐색 중이거나 후보 소진 뒤 병원이 수락하면 목적지를 선택할 수 있는 상태로 변경합니다. */
    public void markAcceptedAvailable() {
        if (status == TransportRequestStatus.SEARCHING
                || status == TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            status = TransportRequestStatus.ACCEPTED_AVAILABLE;
        }
    }

    /** 후보 소진 요청의 새 탐색 회차를 시작합니다. */
    public void resumeSearching() {
        if (status != TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            throw new IllegalStateException("Only an exhausted request can restart search");
        }
        status = TransportRequestStatus.SEARCHING;
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

    /** 철회 복구 탐색이 끝날 때 남은 수락이 있으면 선택 가능 상태를 유지합니다. */
    public void finishWithdrawalRecoverySearch(boolean hasAcceptedOffer) {
        if (currentDestinationOffer != null) {
            return;
        }
        status = hasAcceptedOffer
                ? TransportRequestStatus.ACCEPTED_AVAILABLE
                : TransportRequestStatus.CANDIDATES_EXHAUSTED;
    }

    public boolean hasDestination(HospitalOffer offer) {
        return currentDestinationOffer != null && currentDestinationOffer.getId().equals(offer.getId());
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
