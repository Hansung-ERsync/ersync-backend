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
import com.hansungteam.ersync.hospital.search.api.HospitalAcceptanceWithdrawalResponse;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferDetailResponse;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferListResponse;
import com.hansungteam.ersync.hospital.search.api.HospitalOfferView;
import com.hansungteam.ersync.hospital.search.api.RejectHospitalOfferRequest;
import com.hansungteam.ersync.hospital.search.api.WithdrawHospitalAcceptanceRequest;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptTrigger;
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
import com.hansungteam.ersync.transport.application.SupplementalAssessmentResponseMapper;
import com.hansungteam.ersync.transport.destination.infrastructure.TransportDestinationCommandRepository;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final TransportDestinationCommandRepository destinationCommandRepository;
    private final HospitalCommandFingerprint commandFingerprint;
    private final HospitalSearchService hospitalSearchService;
    private final HospitalOfferOutcomeResolver outcomeResolver;
    private final HospitalClinicalAccessPolicy clinicalAccessPolicy;
    private final SupplementalAssessmentResponseMapper supplementalAssessmentResponseMapper;
    private final AuditService auditService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

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
        PageRequest pageable = PageRequest.of(
                page, size, Sort.by("offeredAt").ascending().and(Sort.by("id").ascending())
        );
        Page<HospitalOffer> result = view == HospitalOfferView.ACTIVE
                ? offerRepository.findActiveForHospital(profile.getId(), pageable)
                : offerRepository.findHistoryForHospital(profile.getId(), pageable);
        Map<Long, Instant> destinationChangedAtByRequestId = latestEffectiveDestinationChangedAt(
                result.getContent()
        );
        Instant now = clock.instant();
        List<HospitalOfferListResponse.Item> items = result.getContent().stream()
                .map(offer -> isHiddenResponseHistory(offer)
                        ? toMinimalHistoryItem(
                                offer,
                                destinationChangedAtByRequestId.get(offer.getTransportRequest().getId())
                        )
                        : toListItem(
                                offer,
                                requireSnapshot(offer),
                                destinationChangedAtByRequestId.get(offer.getTransportRequest().getId())
                        ))
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
        if (isHiddenResponseHistory(offer)) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND);
        }
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
        offer.accept(account, idempotencyKey, fingerprint, decidedAt);
        if (transportRequest.getStatus() == TransportRequestStatus.SEARCHING
                || transportRequest.getStatus() == TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            transportRequest.markAcceptedAvailable();
        }
        stopActiveSearchOnAcceptance(transportRequest, offer, decidedAt);
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

    @Transactional
    public HospitalAcceptanceWithdrawalResponse withdrawAcceptance(
            AuthenticatedAccount principal,
            String offerId,
            String requestedIdempotencyKey,
            WithdrawHospitalAcceptanceRequest request
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        String detail = normalizeWithdrawalDetail(request.reason(), request.detail());
        UserAccount account = requireHospitalAccount(principal, true);
        HospitalOffer offer = lockScopedOffer(principal, offerId);
        byte[] fingerprint = commandFingerprint.withdraw(request.reason(), detail);

        if (offer.getWithdrawalIdempotencyKey() != null) {
            if (!offer.hasWithdrawalIdempotencyKey(idempotencyKey)) {
                throw new CustomException(ErrorCode.HOSPITAL_OFFER_ALREADY_DECIDED);
            }
            if (!offer.hasSameWithdrawalFingerprint(fingerprint)) {
                throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
            }
            return withdrawalResponse(offer, true);
        }
        if (offer.getStatus() != HospitalOfferStatus.ACCEPTED) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_ALREADY_DECIDED);
        }

        TransportRequest transportRequest = offer.getTransportRequest();
        if (transportRequest.getStatus() != TransportRequestStatus.ACCEPTED_AVAILABLE
                && transportRequest.getStatus() != TransportRequestStatus.EN_ROUTE) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }

        HospitalOffer currentDestination = transportRequest.getCurrentDestinationOffer();
        boolean currentDestinationWithdrawn = currentDestination != null
                && currentDestination.getId().equals(offer.getId());
        boolean searchRestarted = currentDestination == null || currentDestinationWithdrawn;
        long acceptedCount = offerRepository.countByTransportRequestIdAndStatus(
                transportRequest.getId(), HospitalOfferStatus.ACCEPTED
        );
        boolean hasRemainingAcceptedOffer = acceptedCount > 1;
        if (currentDestinationWithdrawn) {
            transportRequest.clearDestinationAfterWithdrawal(hasRemainingAcceptedOffer);
        } else if (currentDestination == null) {
            transportRequest.transitionAfterDestinationFreeWithdrawal(hasRemainingAcceptedOffer);
        }

        Instant withdrawnAt = clock.instant();
        HospitalOffer resultingDestination = searchRestarted ? null : currentDestination;
        offer.withdrawAcceptance(
                account,
                request.reason(),
                detail,
                idempotencyKey,
                fingerprint,
                withdrawnAt,
                transportRequest.getStatus(),
                resultingDestination,
                searchRestarted
        );
        offerEventRepository.save(HospitalOfferEvent.recordWithdrawal(
                offer,
                account,
                account.getOrganization(),
                request.reason(),
                detail,
                withdrawnAt
        ));
        if (searchRestarted) {
            hospitalSearchService.startWithdrawalRecovery(transportRequest, withdrawnAt);
        }
        recordWithdrawalSignals(offer, account, withdrawnAt);
        return withdrawalResponse(offer, false);
    }

    private HospitalOffer lockScopedOffer(AuthenticatedAccount principal, String offerId) {
        HospitalOfferRepository.HospitalOfferLockScope scope = offerRepository
                .findLockScope(offerId, principal.organizationId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        TransportRequest lockedRequest = transportRequestRepository.findLockedById(scope.getTransportRequestId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        entityManager.refresh(lockedRequest, LockModeType.PESSIMISTIC_WRITE);
        attemptRepository.findLockedById(scope.getDispatchAttemptId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        HospitalOffer locked = offerRepository.findLockedById(scope.getOfferId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        entityManager.refresh(locked, LockModeType.PESSIMISTIC_WRITE);
        if (!locked.getHospitalProfile().getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND);
        }
        return locked;
    }

    private void stopActiveSearchOnAcceptance(
            TransportRequest transportRequest,
            HospitalOffer acceptedOffer,
            Instant acceptedAt
    ) {
        if (transportRequest.getCurrentDestinationOffer() != null) {
            return;
        }
        attemptRepository.findTopByTransportRequestIdAndStatusOrderByAttemptNumberDesc(
                        transportRequest.getId(),
                        HospitalDispatchAttemptStatus.SEARCHING
                )
                .filter(activeAttempt -> activeAttempt.getTriggerType()
                        != HospitalDispatchAttemptTrigger.ACCEPTANCE_WITHDRAWAL
                        || activeAttempt.getId().equals(acceptedOffer.getDispatchAttempt().getId()))
                .ifPresent(activeAttempt -> activeAttempt.stopOnAcceptance(acceptedAt));
    }

    private boolean isReplayOrThrow(HospitalOffer offer, String idempotencyKey, byte[] fingerprint) {
        if (offer.getResponseIdempotencyKey() == null) {
            if (offer.getStatus() != HospitalOfferStatus.PENDING
                    || offer.getClosedAt() != null
                    || !canRespond(offer.getTransportRequest().getStatus())) {
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

    private String normalizeWithdrawalDetail(
            HospitalAcceptanceWithdrawalReason reason,
            String requestedDetail
    ) {
        if (reason == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        String detail = requestedDetail == null ? null : requestedDetail.trim();
        if (reason == HospitalAcceptanceWithdrawalReason.OTHER) {
            if (detail == null || detail.isEmpty() || detail.length() > 200) {
                throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
            }
            return detail;
        }
        return null;
    }

    private void recordWithdrawalSignals(HospitalOffer offer, UserAccount account, Instant withdrawnAt) {
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.HOSPITAL_ACCEPTANCE_WITHDRAWN,
                RealtimeAudienceType.ACCOUNT,
                offer.getTransportRequest().getOwnerAccount().getPublicId(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                withdrawnAt
        ));
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.HOSPITAL_ACCEPTANCE_WITHDRAWN,
                RealtimeAudienceType.ORGANIZATION,
                account.getOrganization().getPublicId(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                withdrawnAt
        ));
        auditService.record(
                AuditAction.HOSPITAL_ACCEPTANCE_WITHDRAWN,
                account,
                account.getOrganization(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                withdrawnAt
        );
    }

    private HospitalAcceptanceWithdrawalResponse withdrawalResponse(HospitalOffer offer, boolean replay) {
        return new HospitalAcceptanceWithdrawalResponse(
                offer.getPublicId(),
                offer.getStatus(),
                offer.getTransportRequest().getPublicId(),
                offer.getWithdrawalResultingRequestStatus(),
                offer.getWithdrawalResultingDestinationOffer() == null
                        ? null
                        : offer.getWithdrawalResultingDestinationOffer().getPublicId(),
                offer.getWithdrawalReason(),
                offer.getWithdrawalDetail(),
                offer.getWithdrawnAt(),
                Boolean.TRUE.equals(offer.getWithdrawalSearchRestarted()),
                replay
        );
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
            CurrentPatientSnapshot snapshot,
            Instant currentDestinationChangedAt
    ) {
        var demographics = snapshot.getPatientDemographics();
        var preKtas = snapshot.getLatestPreKtasAssessment();
        HospitalOfferOutcomeResult outcome = outcomeResolver.resolve(offer, currentDestinationChangedAt);
        return new HospitalOfferListResponse.Item(
                offer.getPublicId(),
                offer.getTransportRequest().getPublicId(),
                offer.getDispatchAttempt().getAttemptNumber(),
                offer.getTransportRequest().getStatus(),
                offer.getStatus(),
                outcome.outcome(),
                outcome.processedAt(),
                offer.getTransportRequest().hasDestination(offer),
                canWithdraw(offer),
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
                offer.getLastSuccessRouteDistanceMeters(),
                offer.getLastSuccessEtaSeconds(),
                offer.getLastSuccessEtaCalculatedAt(),
                snapshot.getLastClinicalUpdateAt(),
                offer.getOfferedAt(),
                offer.getRespondedAt(),
                offer.getWithdrawalReason(),
                offer.getWithdrawalDetail(),
                offer.getWithdrawnAt(),
                canConfirmHandoff(offer),
                offer.getTransportRequest().getHandoffRequestedAt(),
                offer.getTransportRequest().getCompletedAt(),
                offer.getTransportRequest().getCancelledAt(),
                offer.getTransportRequest().getCancellationReason()
        );
    }

    private HospitalOfferListResponse.Item toMinimalHistoryItem(
            HospitalOffer offer,
            Instant currentDestinationChangedAt
    ) {
        HospitalOfferOutcomeResult outcome = outcomeResolver.resolve(offer, currentDestinationChangedAt);
        return new HospitalOfferListResponse.Item(
                offer.getPublicId(),
                offer.getTransportRequest().getPublicId(),
                null,
                offer.getTransportRequest().getStatus(),
                offer.getStatus(),
                outcome.outcome(),
                outcome.processedAt(),
                false,
                canWithdraw(offer),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                offer.getRespondedAt(),
                offer.getWithdrawalReason(),
                offer.getWithdrawalDetail(),
                offer.getWithdrawnAt(),
                canConfirmHandoff(offer),
                offer.getTransportRequest().getHandoffRequestedAt(),
                offer.getTransportRequest().getCompletedAt(),
                offer.getTransportRequest().getCancelledAt(),
                offer.getTransportRequest().getCancellationReason()
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
        HospitalOfferOutcomeResult outcome = outcomeResolver.resolve(offer, null);
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
                outcome.outcome(),
                outcome.processedAt(),
                offer.getTransportRequest().hasDestination(offer),
                canWithdraw(offer),
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
                clinicalAccessPolicy.canRead(offer)
                        ? supplementalAssessmentResponseMapper.map(snapshot)
                        : null,
                new HospitalOfferDetailResponse.Requester(
                        offer.getTransportRequest().getOrganization().getName(),
                        visibleHospitalContact(offer)
                ),
                new HospitalOfferDetailResponse.Route(
                        offer.getStraightLineDistanceMeters(),
                        offer.getRouteEstimateStatus(),
                        offer.getRouteDistanceMeters(),
                        offer.getEtaSeconds(),
                        offer.getEtaCalculatedAt(),
                        offer.getLastSuccessRouteDistanceMeters(),
                        offer.getLastSuccessEtaSeconds(),
                        offer.getLastSuccessEtaCalculatedAt()
                ),
                new HospitalOfferDetailResponse.Timing(
                        offer.getTransportRequest().getServerReceivedAt(),
                        offer.getOfferedAt(),
                        snapshot.getLastClinicalUpdateAt()
                ),
                offer.getRejectionReason(),
                offer.getRejectionDetail(),
                offer.getWithdrawalReason(),
                offer.getWithdrawalDetail(),
                offer.getRespondedAt(),
                offer.getWithdrawnAt(),
                canConfirmHandoff(offer),
                offer.getTransportRequest().getHandoffRequestedAt(),
                offer.getTransportRequest().getCompletedAt(),
                offer.getTransportRequest().getCancelledAt(),
                offer.getTransportRequest().getCancellationReason(),
                now
        );
    }

    private Map<Long, Instant> latestEffectiveDestinationChangedAt(List<HospitalOffer> offers) {
        Map<Long, Long> currentDestinationIdByRequestId = new HashMap<>();
        for (HospitalOffer offer : offers) {
            TransportRequest request = offer.getTransportRequest();
            if (request.getCurrentDestinationOffer() != null
                    && request.getStatus() != TransportRequestStatus.COMPLETED
                    && request.getStatus() != TransportRequestStatus.CANCELLED
                    && !request.hasDestination(offer)
                    && (offer.getStatus() == HospitalOfferStatus.PENDING
                            || offer.getStatus() == HospitalOfferStatus.ACCEPTED)) {
                currentDestinationIdByRequestId.put(
                        request.getId(),
                        request.getCurrentDestinationOffer().getId()
                );
            }
        }
        if (currentDestinationIdByRequestId.isEmpty()) {
            return Map.of();
        }

        Map<Long, Instant> changedAtByRequestId = new HashMap<>();
        destinationCommandRepository.findLatestEffectiveDestinations(currentDestinationIdByRequestId.keySet())
                .forEach(destination -> {
                    Long currentDestinationId = currentDestinationIdByRequestId.get(
                            destination.getTransportRequestId()
                    );
                    if (destination.getDestinationOfferId().equals(currentDestinationId)) {
                        changedAtByRequestId.put(
                                destination.getTransportRequestId(),
                                destination.getOccurredAt()
                        );
                    }
                });
        return changedAtByRequestId;
    }

    private boolean isHiddenResponseHistory(HospitalOffer offer) {
        if (offer.getTransportRequest().getStatus() == TransportRequestStatus.COMPLETED
                || offer.getTransportRequest().getStatus() == TransportRequestStatus.CANCELLED) {
            return true;
        }
        if (offer.getStatus() == HospitalOfferStatus.ACCEPTANCE_WITHDRAWN) {
            return true;
        }
        if (offer.getStatus() == HospitalOfferStatus.PENDING
                && offer.getTransportRequest().getCurrentDestinationOffer() != null) {
            return true;
        }
        return offer.getStatus() == HospitalOfferStatus.ACCEPTED
                && offer.getTransportRequest().getCurrentDestinationOffer() != null
                && !offer.getTransportRequest().hasDestination(offer);
    }

    private boolean canWithdraw(HospitalOffer offer) {
        if (offer.getStatus() != HospitalOfferStatus.ACCEPTED) {
            return false;
        }
        TransportRequestStatus status = offer.getTransportRequest().getStatus();
        return status == TransportRequestStatus.ACCEPTED_AVAILABLE
                || status == TransportRequestStatus.EN_ROUTE;
    }

    private boolean canConfirmHandoff(HospitalOffer offer) {
        return offer.getClosedAt() == null
                && offer.getStatus() == HospitalOfferStatus.ACCEPTED
                && offer.getTransportRequest().getStatus() == TransportRequestStatus.HANDOFF_REQUESTED
                && offer.getTransportRequest().hasDestination(offer);
    }

    private boolean canRespond(TransportRequestStatus status) {
        return status == TransportRequestStatus.SEARCHING
                || status == TransportRequestStatus.CANDIDATES_EXHAUSTED
                || status == TransportRequestStatus.ACCEPTED_AVAILABLE
                || status == TransportRequestStatus.EN_ROUTE;
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
