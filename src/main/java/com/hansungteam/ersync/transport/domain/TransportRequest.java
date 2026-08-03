package com.hansungteam.ersync.transport.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
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
