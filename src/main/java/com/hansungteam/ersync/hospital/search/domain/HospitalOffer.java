package com.hansungteam.ersync.hospital.search.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 병원 한 곳에 전달된 이송 요청과 현재 응답·ETA 스냅샷입니다. */
@Entity
@Table(name = "hospital_offers")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_attempt_id", nullable = false)
    private HospitalDispatchAttempt dispatchAttempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "search_round_id", nullable = false)
    private HospitalSearchRound searchRound;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_profile_id", nullable = false)
    private HospitalProfile hospitalProfile;

    @Column(name = "hospital_name_snapshot", nullable = false, length = 100)
    private String hospitalNameSnapshot;

    @Column(name = "hospital_contact_snapshot", nullable = false, length = 30)
    private String hospitalContactSnapshot;

    @Column(name = "hospital_latitude_snapshot", nullable = false, precision = 10, scale = 7)
    private BigDecimal hospitalLatitudeSnapshot;

    @Column(name = "hospital_longitude_snapshot", nullable = false, precision = 10, scale = 7)
    private BigDecimal hospitalLongitudeSnapshot;

    @Column(name = "straight_line_distance_m", nullable = false)
    private long straightLineDistanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private HospitalOfferStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_estimate_status", nullable = false, length = 20)
    private RouteEstimateStatus routeEstimateStatus;

    @Column(name = "route_distance_m")
    private Long routeDistanceMeters;

    @Column(name = "eta_seconds")
    private Long etaSeconds;

    @Column(name = "eta_calculated_at", columnDefinition = "datetime(6)")
    private Instant etaCalculatedAt;

    @Column(name = "eta_attempt_count", nullable = false)
    private int etaAttemptCount;

    @Column(name = "eta_next_attempt_at", columnDefinition = "datetime(6)")
    private Instant etaNextAttemptAt;

    @Column(name = "response_idempotency_key", length = 100)
    private String responseIdempotencyKey;

    @Column(name = "response_fingerprint", columnDefinition = "binary(32)")
    private byte[] responseFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 50)
    private HospitalRejectionReason rejectionReason;

    @Column(name = "rejection_detail", length = 200)
    private String rejectionDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_account_id")
    private UserAccount respondedByAccount;

    @Column(name = "responded_at", columnDefinition = "datetime(6)")
    private Instant respondedAt;

    @Column(name = "offered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant offeredAt;

    @Column(name = "closed_at", columnDefinition = "datetime(6)")
    private Instant closedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private HospitalOffer(
            TransportRequest transportRequest,
            HospitalDispatchAttempt dispatchAttempt,
            HospitalSearchRound searchRound,
            HospitalProfile hospitalProfile,
            long straightLineDistanceMeters,
            Instant offeredAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.dispatchAttempt = dispatchAttempt;
        this.searchRound = searchRound;
        this.hospitalProfile = hospitalProfile;
        this.hospitalNameSnapshot = hospitalProfile.getOrganization().getName();
        this.hospitalContactSnapshot = hospitalProfile.getContact();
        this.hospitalLatitudeSnapshot = hospitalProfile.getLatitude();
        this.hospitalLongitudeSnapshot = hospitalProfile.getLongitude();
        this.straightLineDistanceMeters = straightLineDistanceMeters;
        this.status = HospitalOfferStatus.PENDING;
        this.routeEstimateStatus = RouteEstimateStatus.CALCULATING;
        this.etaNextAttemptAt = offeredAt;
        this.offeredAt = offeredAt;
        this.createdAt = offeredAt;
        this.updatedAt = offeredAt;
    }

    public static HospitalOffer offer(
            TransportRequest transportRequest,
            HospitalDispatchAttempt dispatchAttempt,
            HospitalSearchRound searchRound,
            HospitalProfile hospitalProfile,
            long straightLineDistanceMeters,
            Instant offeredAt
    ) {
        return new HospitalOffer(
                transportRequest,
                dispatchAttempt,
                searchRound,
                hospitalProfile,
                straightLineDistanceMeters,
                offeredAt
        );
    }

    public boolean hasSameResponseFingerprint(byte[] fingerprint) {
        return Arrays.equals(responseFingerprint, fingerprint);
    }

    public boolean hasResponseIdempotencyKey(String idempotencyKey) {
        return responseIdempotencyKey != null && responseIdempotencyKey.equals(idempotencyKey);
    }

    /** 병원의 수락과 행위자를 제안에 확정합니다. */
    public void accept(
            UserAccount respondedByAccount,
            String idempotencyKey,
            byte[] fingerprint,
            Instant respondedAt
    ) {
        requirePending();
        status = HospitalOfferStatus.ACCEPTED;
        responseIdempotencyKey = idempotencyKey;
        responseFingerprint = Arrays.copyOf(fingerprint, fingerprint.length);
        this.respondedByAccount = respondedByAccount;
        this.respondedAt = respondedAt;
    }

    /** 병원의 거절 사유와 행위자를 제안에 확정합니다. */
    public void reject(
            UserAccount respondedByAccount,
            HospitalRejectionReason rejectionReason,
            String rejectionDetail,
            String idempotencyKey,
            byte[] fingerprint,
            Instant respondedAt
    ) {
        requirePending();
        status = HospitalOfferStatus.REJECTED;
        responseIdempotencyKey = idempotencyKey;
        responseFingerprint = Arrays.copyOf(fingerprint, fingerprint.length);
        this.rejectionReason = rejectionReason;
        this.rejectionDetail = rejectionDetail;
        this.respondedByAccount = respondedByAccount;
        this.respondedAt = respondedAt;
        this.closedAt = respondedAt;
    }

    /** 마지막 응답 창이 끝난 미응답 제안을 시스템이 닫습니다. */
    public void markNoResponse(Instant closedAt) {
        if (status != HospitalOfferStatus.PENDING) {
            return;
        }
        status = HospitalOfferStatus.NO_RESPONSE;
        this.closedAt = closedAt;
    }

    /** 외부 호출 전에 짧은 lease를 잡아 같은 ETA 작업의 동시 실행을 줄입니다. */
    public void reserveRouteEstimate(Instant leaseUntil) {
        if (routeEstimateStatus != RouteEstimateStatus.CALCULATING) {
            throw new IllegalStateException("Only a calculating route estimate can be reserved");
        }
        etaAttemptCount++;
        etaNextAttemptAt = leaseUntil;
    }

    /** 네이버 계산 결과를 도로 거리와 초 단위 ETA로 확정합니다. */
    public void completeRouteEstimate(long distanceMeters, long etaSeconds, Instant calculatedAt) {
        if (distanceMeters < 0 || etaSeconds < 0) {
            throw new IllegalArgumentException("Route distance and ETA cannot be negative");
        }
        routeEstimateStatus = RouteEstimateStatus.AVAILABLE;
        routeDistanceMeters = distanceMeters;
        this.etaSeconds = etaSeconds;
        etaCalculatedAt = calculatedAt;
        etaNextAttemptAt = null;
    }

    /** 일시 오류 뒤 다시 계산할 서버 시각을 저장합니다. */
    public void scheduleRouteEstimateRetry(Instant nextAttemptAt) {
        if (routeEstimateStatus != RouteEstimateStatus.CALCULATING) {
            return;
        }
        etaNextAttemptAt = nextAttemptAt;
    }

    /** 키 없음·영구 오류·재시도 소진을 제안 흐름과 분리해 종료합니다. */
    public void markRouteEstimateUnavailable() {
        routeEstimateStatus = RouteEstimateStatus.UNAVAILABLE;
        routeDistanceMeters = null;
        etaSeconds = null;
        etaCalculatedAt = null;
        etaNextAttemptAt = null;
    }

    private void requirePending() {
        if (status != HospitalOfferStatus.PENDING) {
            throw new IllegalStateException("Only a pending hospital offer can be decided");
        }
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
