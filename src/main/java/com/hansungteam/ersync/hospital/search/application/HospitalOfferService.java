package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferDecisionResponse;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferDetailResponse;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferListResponse;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferView;
import com.hansungteam.ersync.hospital.search.api.RejectHospitalOfferRequest;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferEvent;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferEventType;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalRejectionReason;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferEventRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.application.IdempotencyKeyPolicy;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 병원 조직에 전달된 제안만 조회·응답하도록 조직 격리와 상태 전이를 담당합니다. */
@Service
@RequiredArgsConstructor
public class HospitalOfferService {

    private static final String OFFER_AGGREGATE = "HOSPITAL_OFFER";

    private final UserAccountRepository userAccountRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final HospitalOfferRepository offerRepository;
    private final HospitalOfferEventRepository offerEventRepository;
    private final HospitalDispatchAttemptRepository attemptRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final CurrentPatientSnapshotRepository snapshotRepository;
    private final RealtimeOutboxEventRepository outboxEventRepository;
    private final HospitalCommandFingerprint commandFingerprint;
    private final HospitalSearchService hospitalSearchService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public HospitalOfferListResponse list(
            AuthenticatedAccount principal,
            HospitalOfferView view,
            int page,
            int size
    ) {
        HospitalProfile profile = requireHospitalContext(principal);
        if (page < 0 || size < 1 || size > 100) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        Set<HospitalOfferStatus> statuses = view == HospitalOfferView.ACTIVE
                ? Set.of(HospitalOfferStatus.PENDING, HospitalOfferStatus.ACCEPTED)
                : Set.of(HospitalOfferStatus.REJECTED, HospitalOfferStatus.NO_RESPONSE);
        Page<HospitalOffer> result = offerRepository.findByHospitalProfileIdAndStatusIn(
                profile.getId(),
                statuses,
                PageRequest.of(page, size, Sort.by("offeredAt").ascending().and(Sort.by("id").ascending()))
        );
        Instant now = clock.instant();
        List<HospitalOfferListResponse.Item> items = result.getContent().stream()
                .map(offer -> toListItem(offer, requireSnapshot(offer)))
                .toList();
        return new HospitalOfferListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                now
        );
    }

    @Transactional(readOnly = true)
    public HospitalOfferDetailResponse detail(AuthenticatedAccount principal, String offerId) {
        HospitalProfile profile = requireHospitalContext(principal);
        HospitalOffer offer = offerRepository
                .findByPublicIdAndHospitalProfileOrganizationPublicId(
                        offerId,
                        profile.getOrganization().getPublicId()
                )
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        return toDetail(offer, requireSnapshot(offer), clock.instant());
    }

    @Transactional
    public HospitalOfferDecisionResponse accept(
            AuthenticatedAccount principal,
            String offerId,
            String requestedIdempotencyKey
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireHospitalAccount(principal, true);
        HospitalOffer offer = lockScopedOffer(principal, offerId);
        byte[] fingerprint = commandFingerprint.accept();
        if (isReplayOrThrow(offer, idempotencyKey, fingerprint)) {
            return decisionResponse(offer, true);
        }

        Instant decidedAt = clock.instant();
        TransportRequest transportRequest = offer.getTransportRequest();
        HospitalDispatchAttempt attempt = offer.getDispatchAttempt();
        offer.accept(account, idempotencyKey, fingerprint, decidedAt);
        if (transportRequest.getStatus() == TransportRequestStatus.SEARCHING) {
            transportRequest.markAcceptedAvailable();
            attempt.stopOnAcceptance(decidedAt);
        }
        recordDecision(
                offer,
                HospitalOfferEventType.ACCEPTED,
                RealtimeEventType.HOSPITAL_OFFER_ACCEPTED,
                AuditAction.HOSPITAL_OFFER_ACCEPTED,
                account,
                null,
                null,
                decidedAt
        );
        return decisionResponse(offer, false);
    }

    @Transactional
    public HospitalOfferDecisionResponse reject(
            AuthenticatedAccount principal,
            String offerId,
            String requestedIdempotencyKey,
            RejectHospitalOfferRequest request
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        String detail = normalizeRejectionDetail(request.reason(), request.detail());
        UserAccount account = requireHospitalAccount(principal, true);
        HospitalOffer offer = lockScopedOffer(principal, offerId);
        byte[] fingerprint = commandFingerprint.reject(request.reason(), detail);
        if (isReplayOrThrow(offer, idempotencyKey, fingerprint)) {
            return decisionResponse(offer, true);
        }

        Instant decidedAt = clock.instant();
        offer.reject(account, request.reason(), detail, idempotencyKey, fingerprint, decidedAt);
        recordDecision(
                offer,
                HospitalOfferEventType.REJECTED,
                RealtimeEventType.HOSPITAL_OFFER_REJECTED,
                AuditAction.HOSPITAL_OFFER_REJECTED,
                account,
                request.reason(),
                detail,
                decidedAt
        );
        hospitalSearchService.exhaustIfMaximumRadiusAllRejected(
                offer.getDispatchAttempt(),
                offer.getTransportRequest(),
                decidedAt
        );
        return decisionResponse(offer, false);
    }

    private HospitalOffer lockScopedOffer(AuthenticatedAccount principal, String offerId) {
        HospitalOffer scoped = offerRepository
                .findByPublicIdAndHospitalProfileOrganizationPublicId(offerId, principal.organizationId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        transportRequestRepository.findLockedById(scoped.getTransportRequest().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        attemptRepository.findLockedById(scoped.getDispatchAttempt().getId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        HospitalOffer locked = offerRepository.findLockedById(scoped.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        if (!locked.getHospitalProfile().getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND);
        }
        return locked;
    }

    private boolean isReplayOrThrow(HospitalOffer offer, String idempotencyKey, byte[] fingerprint) {
        if (offer.getResponseIdempotencyKey() == null) {
            if (offer.getStatus() != HospitalOfferStatus.PENDING) {
                throw new CustomException(ErrorCode.HOSPITAL_OFFER_ALREADY_DECIDED);
            }
            return false;
        }
        if (!offer.hasResponseIdempotencyKey(idempotencyKey)) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_ALREADY_DECIDED);
        }
        if (!offer.hasSameResponseFingerprint(fingerprint)) {
            throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
        }
        return true;
    }

    private void recordDecision(
            HospitalOffer offer,
            HospitalOfferEventType eventType,
            RealtimeEventType realtimeEventType,
            AuditAction auditAction,
            UserAccount account,
            HospitalRejectionReason rejectionReason,
            String rejectionDetail,
            Instant decidedAt
    ) {
        offerEventRepository.save(HospitalOfferEvent.record(
                offer,
                eventType,
                account,
                account.getOrganization(),
                rejectionReason,
                rejectionDetail,
                decidedAt
        ));
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                realtimeEventType,
                RealtimeAudienceType.ACCOUNT,
                offer.getTransportRequest().getOwnerAccount().getPublicId(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                decidedAt
        ));
        auditService.record(
                auditAction,
                account,
                account.getOrganization(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                decidedAt
        );
    }

    private HospitalOfferDecisionResponse decisionResponse(HospitalOffer offer, boolean replay) {
        return new HospitalOfferDecisionResponse(
                offer.getPublicId(),
                offer.getStatus(),
                offer.getTransportRequest().getPublicId(),
                offer.getTransportRequest().getStatus(),
                offer.getRespondedAt(),
                replay
        );
    }

    private UserAccount requireHospitalAccount(AuthenticatedAccount principal, boolean lock) {
        if (principal.role() != UserRole.HOSPITAL_STAFF || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = (lock
                ? userAccountRepository.findLockedByPublicId(principal.accountId())
                : userAccountRepository.findByPublicId(principal.accountId()))
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.HOSPITAL_STAFF
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return account;
    }

    private HospitalProfile requireHospitalContext(AuthenticatedAccount principal) {
        UserAccount account = requireHospitalAccount(principal, false);
        return hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .filter(profile -> profile.getOrganization().getPublicId().equals(principal.organizationId()))
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
    }

    private CurrentPatientSnapshot requireSnapshot(HospitalOffer offer) {
        return snapshotRepository.findByTransportRequestPublicId(offer.getTransportRequest().getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
    }

    private HospitalOfferListResponse.Item toListItem(
            HospitalOffer offer,
            CurrentPatientSnapshot snapshot
    ) {
        var demographics = snapshot.getPatientDemographics();
        var preKtas = snapshot.getLatestPreKtasAssessment();
        return new HospitalOfferListResponse.Item(
                offer.getPublicId(),
                offer.getTransportRequest().getPublicId(),
                offer.getDispatchAttempt().getAttemptNumber(),
                offer.getTransportRequest().getStatus(),
                offer.getStatus(),
                demographics.getAgeStatus().name(),
                demographics.getAgeYears(),
                demographics.getSex().name(),
                preKtas.getClassificationStatus().name(),
                preKtas.getLevel(),
                enumName(preKtas.getExceptionReason()),
                offer.getStraightLineDistanceMeters(),
                offer.getRouteEstimateStatus(),
                offer.getRouteDistanceMeters(),
                offer.getEtaSeconds(),
                offer.getOfferedAt()
        );
    }

    private HospitalOfferDetailResponse toDetail(
            HospitalOffer offer,
            CurrentPatientSnapshot snapshot,
            Instant now
    ) {
        var demographics = snapshot.getPatientDemographics();
        var incident = snapshot.getIncidentAssessment();
        var preKtas = snapshot.getLatestPreKtasAssessment();
        var consciousness = snapshot.getLatestConsciousnessAssessment();
        var vitalSigns = snapshot.getLatestVitalSignSet();
        List<HospitalOfferDetailResponse.VitalSign> measurements = vitalSigns.getMeasurements().stream()
                .map(measurement -> new HospitalOfferDetailResponse.VitalSign(
                        measurement.getMeasurementType().name(),
                        measurement.getState().name(),
                        measurement.getPrimaryValue(),
                        measurement.getSecondaryValue(),
                        enumName(measurement.getUnavailableReason()),
                        measurement.getUnavailableDetail()
                ))
                .toList();
        List<HospitalOfferDetailResponse.Treatment> treatments = snapshot.getCurrentTreatments().stream()
                .map(treatment -> {
                    var details = treatment.getDetails();
                    return new HospitalOfferDetailResponse.Treatment(
                            treatment.getTreatmentType().name(),
                            enumName(treatment.getAttemptResult()),
                            treatment.getPerformedAt(),
                            details == null ? null : details.getMethod(),
                            details == null ? null : details.getDevice(),
                            details == null ? null : details.getFlowRateLpm(),
                            details == null ? null : details.getCurrentStatus(),
                            details == null ? null : details.getMedicationName(),
                            details == null ? null : details.getDose(),
                            details == null ? null : details.getRoute(),
                            details == null ? null : details.getSite(),
                            details == null ? null : details.getDetail()
                    );
                })
                .toList();
        return new HospitalOfferDetailResponse(
                offer.getPublicId(),
                offer.getTransportRequest().getPublicId(),
                offer.getDispatchAttempt().getAttemptNumber(),
                offer.getTransportRequest().getStatus(),
                offer.getStatus(),
                new HospitalOfferDetailResponse.Patient(
                        demographics.getAgeStatus().name(),
                        demographics.getAgeYears(),
                        demographics.getSex().name()
                ),
                new HospitalOfferDetailResponse.Incident(
                        incident.getOccurrenceType().name(),
                        enumName(incident.getMechanism()),
                        names(incident.getInjurySites()),
                        incident.getPrimarySymptom().name(),
                        incident.getPrimarySymptomDetail(),
                        names(incident.getSecondarySymptoms()),
                        incident.getOnsetTimeStatus().name(),
                        incident.getOnsetAt()
                ),
                new HospitalOfferDetailResponse.PreKtas(
                        preKtas.getClassificationStatus().name(),
                        preKtas.getLevel(),
                        enumName(preKtas.getExceptionReason()),
                        preKtas.getExceptionDetail(),
                        preKtas.getAssessedAt(),
                        preKtas.getStandardVersion()
                ),
                new HospitalOfferDetailResponse.Consciousness(
                        consciousness.getAvpu().name(),
                        enumName(consciousness.getUnassessableReason()),
                        consciousness.getUnassessableDetail(),
                        consciousness.getObservedAt()
                ),
                new HospitalOfferDetailResponse.VitalSigns(vitalSigns.getMeasuredAt(), measurements),
                treatments,
                new HospitalOfferDetailResponse.Requester(
                        offer.getTransportRequest().getOrganization().getName(),
                        visibleHospitalContact(offer)
                ),
                new HospitalOfferDetailResponse.Route(
                        offer.getStraightLineDistanceMeters(),
                        offer.getRouteEstimateStatus(),
                        offer.getRouteDistanceMeters(),
                        offer.getEtaSeconds(),
                        offer.getEtaCalculatedAt()
                ),
                new HospitalOfferDetailResponse.Timing(
                        offer.getTransportRequest().getServerReceivedAt(),
                        offer.getOfferedAt(),
                        snapshot.getLastClinicalUpdateAt()
                ),
                offer.getRejectionReason(),
                offer.getRejectionDetail(),
                offer.getRespondedAt(),
                now
        );
    }

    private String visibleHospitalContact(HospitalOffer offer) {
        if (offer.getStatus() == HospitalOfferStatus.PENDING
                || offer.getStatus() == HospitalOfferStatus.ACCEPTED) {
            return offer.getTransportRequest().getCallbackContact();
        }
        String contact = offer.getTransportRequest().getCallbackContact();
        String digits = contact.replaceAll("[^0-9]", "");
        return digits.length() < 4 ? "****" : "****-" + digits.substring(digits.length() - 4);
    }

    private String normalizeRejectionDetail(HospitalRejectionReason reason, String detail) {
        String normalized = detail == null || detail.isBlank() ? null : detail.trim();
        if (reason == HospitalRejectionReason.OTHER && normalized == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return normalized;
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private Set<String> names(Set<? extends Enum<?>> values) {
        return values.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
