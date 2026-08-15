package com.hansungteam.ersync.hospital.search.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
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

    @Column(name = "route_estimate_generation", nullable = false)
    private long routeEstimateGeneration;

    @Column(name = "last_success_route_distance_m")
    private Long lastSuccessRouteDistanceMeters;

    @Column(name = "last_success_eta_seconds")
    private Long lastSuccessEtaSeconds;

    @Column(name = "last_success_eta_calculated_at", columnDefinition = "datetime(6)")
    private Instant lastSuccessEtaCalculatedAt;

    @Column(name = "response_idempotency_key", length = 100)
    private String responseIdempotencyKey;

    @Column(name = "response_fingerprint", columnDefinition = "binary(32)")
    private byte[] responseFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 50)
    private HospitalRejectionReason rejectionReason;

    @Column(name = "rejection_detail", length = 200)
    private String rejectionDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reason", length = 50)
    private HospitalAcceptanceWithdrawalReason withdrawalReason;

    @Column(name = "withdrawal_detail", length = 200)
    private String withdrawalDetail;

    @Column(name = "withdrawal_idempotency_key", length = 100)
    private String withdrawalIdempotencyKey;

    @Column(name = "withdrawal_fingerprint", columnDefinition = "binary(32)")
    private byte[] withdrawalFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responded_by_account_id")
    private UserAccount respondedByAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawn_by_account_id")
    private UserAccount withdrawnByAccount;

    @Column(name = "responded_at", columnDefinition = "datetime(6)")
    private Instant respondedAt;

    @Column(name = "withdrawn_at", columnDefinition = "datetime(6)")
    private Instant withdrawnAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_resulting_request_status", length = 30)
    private TransportRequestStatus withdrawalResultingRequestStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "withdrawal_resulting_destination_offer_id")
    private HospitalOffer withdrawalResultingDestinationOffer;

    @Column(name = "withdrawal_search_restarted")
    private Boolean withdrawalSearchRestarted;

    @Column(name = "offered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant offeredAt;

    @Column(name = "clinical_visibility_cutoff_at", columnDefinition = "datetime(6)")
    private Instant clinicalVisibilityCutoffAt;

    @Column(name = "frozen_last_clinical_update_at", columnDefinition = "datetime(6)")
    private Instant frozenLastClinicalUpdateAt;

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

    public boolean hasWithdrawalIdempotencyKey(String idempotencyKey) {
        return withdrawalIdempotencyKey != null && withdrawalIdempotencyKey.equals(idempotencyKey);
    }

    public boolean hasSameWithdrawalFingerprint(byte[] fingerprint) {
        return Arrays.equals(withdrawalFingerprint, fingerprint);
    }

    /** 이미 수락한 병원이 수락을 철회한 사실과 멱등성 정보를 확정합니다. */
    public void withdrawAcceptance(
            UserAccount withdrawnByAccount,
            HospitalAcceptanceWithdrawalReason withdrawalReason,
            String withdrawalDetail,
            String idempotencyKey,
            byte[] fingerprint,
            Instant withdrawnAt,
            TransportRequestStatus resultingRequestStatus,
            HospitalOffer resultingDestinationOffer,
            boolean searchRestarted
    ) {
        if (status != HospitalOfferStatus.ACCEPTED) {
            throw new IllegalStateException("Only an accepted hospital offer can be withdrawn");
        }
        status = HospitalOfferStatus.ACCEPTANCE_WITHDRAWN;
        this.withdrawnByAccount = withdrawnByAccount;
        this.withdrawalReason = withdrawalReason;
        this.withdrawalDetail = withdrawalDetail;
        this.withdrawalIdempotencyKey = idempotencyKey;
        this.withdrawalFingerprint = Arrays.copyOf(fingerprint, fingerprint.length);
        this.withdrawnAt = withdrawnAt;
        this.closedAt = withdrawnAt;
        this.withdrawalResultingRequestStatus = resultingRequestStatus;
        this.withdrawalResultingDestinationOffer = resultingDestinationOffer;
        this.withdrawalSearchRestarted = searchRestarted;
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

    /** 요청 종료 시 병원의 실제 응답 상태는 보존하고 활성 제안만 닫습니다. */
    public void close(Instant closedAt) {
        if (this.closedAt == null) {
            this.closedAt = closedAt;
            etaNextAttemptAt = null;
        }
    }

    /** 목적지 이후 새 임상정보가 노출되지 않도록 최초 공개 종료 시각을 고정합니다. */
    public void freezeClinicalVisibility(Instant cutoffAt, Instant lastClinicalUpdateAt) {
        if (clinicalVisibilityCutoffAt != null) {
            return;
        }
        clinicalVisibilityCutoffAt = cutoffAt;
        frozenLastClinicalUpdateAt = lastClinicalUpdateAt.isAfter(cutoffAt)
                ? cutoffAt
                : lastClinicalUpdateAt;
    }

    /** 목적지로 선택되거나 목적지가 해제된 활성 제안에 최신 임상정보 접근을 복원합니다. */
    public void allowLiveClinicalVisibility() {
        clinicalVisibilityCutoffAt = null;
        frozenLastClinicalUpdateAt = null;
    }

    /** 외부 호출 전에 짧은 lease를 잡아 같은 ETA 작업의 동시 실행을 줄입니다. */
    public void reserveRouteEstimate(Instant leaseUntil) {
        if (routeEstimateStatus != RouteEstimateStatus.CALCULATING) {
            throw new IllegalStateException("Only a calculating route estimate can be reserved");
        }
        etaAttemptCount++;
        etaNextAttemptAt = leaseUntil;
    }

    /** 최신 위치 또는 새 목적지를 기준으로 이전 계산과 구분되는 ETA 세대를 예약합니다. */
    public void scheduleRouteEstimateRecalculation(Instant scheduledAt) {
        routeEstimateGeneration++;
        routeEstimateStatus = RouteEstimateStatus.CALCULATING;
        routeDistanceMeters = null;
        etaSeconds = null;
        etaCalculatedAt = null;
        etaAttemptCount = 0;
        etaNextAttemptAt = scheduledAt;
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
        lastSuccessRouteDistanceMeters = distanceMeters;
        lastSuccessEtaSeconds = etaSeconds;
        lastSuccessEtaCalculatedAt = calculatedAt;
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
        if (status != HospitalOfferStatus.PENDING || closedAt != null) {
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
