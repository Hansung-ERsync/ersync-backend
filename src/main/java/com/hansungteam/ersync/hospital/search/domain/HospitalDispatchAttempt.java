package com.hansungteam.ersync.hospital.search.domain;

import com.hansungteam.ersync.transport.domain.TransportRequest;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 후보 소진 후 재전송을 포함한 요청별 병원 탐색 한 회차입니다. */
@Entity
@Table(name = "hospital_dispatch_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalDispatchAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HospitalDispatchAttemptStatus status;

    @Column(name = "current_radius_km", nullable = false)
    private int currentRadiusKm;

    @Column(name = "candidate_shortage", nullable = false)
    private boolean candidateShortage;

    @Column(name = "next_expansion_at", columnDefinition = "datetime(6)")
    private Instant nextExpansionAt;

    @Column(name = "retry_idempotency_key", length = 100)
    private String retryIdempotencyKey;

    @Column(name = "retry_fingerprint", columnDefinition = "binary(32)")
    private byte[] retryFingerprint;

    @Column(name = "started_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant startedAt;

    @Column(name = "ended_at", columnDefinition = "datetime(6)")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private HospitalDispatchAttempt(
            TransportRequest transportRequest,
            int attemptNumber,
            String retryIdempotencyKey,
            byte[] retryFingerprint,
            Instant startedAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.attemptNumber = attemptNumber;
        this.status = HospitalDispatchAttemptStatus.SEARCHING;
        this.retryIdempotencyKey = retryIdempotencyKey;
        this.retryFingerprint = retryFingerprint == null
                ? null
                : Arrays.copyOf(retryFingerprint, retryFingerprint.length);
        this.startedAt = startedAt;
        this.createdAt = startedAt;
        this.updatedAt = startedAt;
    }

    public static HospitalDispatchAttempt initial(TransportRequest transportRequest, Instant startedAt) {
        return new HospitalDispatchAttempt(transportRequest, 1, null, null, startedAt);
    }

    public static HospitalDispatchAttempt retry(
            TransportRequest transportRequest,
            int attemptNumber,
            String retryIdempotencyKey,
            byte[] retryFingerprint,
            Instant startedAt
    ) {
        return new HospitalDispatchAttempt(
                transportRequest,
                attemptNumber,
                retryIdempotencyKey,
                retryFingerprint,
                startedAt
        );
    }

    public void scheduleNextExpansion(int radiusKm, boolean candidateShortage, Instant nextExpansionAt) {
        this.currentRadiusKm = radiusKm;
        this.candidateShortage = candidateShortage;
        this.nextExpansionAt = nextExpansionAt;
    }

    public void stopOnAcceptance(Instant endedAt) {
        status = HospitalDispatchAttemptStatus.STOPPED_ON_ACCEPTANCE;
        nextExpansionAt = null;
        this.endedAt = endedAt;
    }

    public void exhaust(Instant endedAt) {
        status = HospitalDispatchAttemptStatus.EXHAUSTED;
        nextExpansionAt = null;
        this.endedAt = endedAt;
    }

    public boolean hasSameRetryFingerprint(byte[] fingerprint) {
        return Arrays.equals(retryFingerprint, fingerprint);
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
