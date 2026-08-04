package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.api.DispatchAttemptResponse;
import com.hansungteam.ersync.hospital.search.api.TransportHospitalSearchResponse;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.application.IdempotencyKeyPolicy;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** 구급대원의 소유 요청에 한해 검색 현황 조회와 후보 소진 재전송을 제공합니다. */
@Service
@RequiredArgsConstructor
public class TransportHospitalSearchService {

    private static final String ATTEMPT_AGGREGATE = "HOSPITAL_DISPATCH_ATTEMPT";

    private final UserAccountRepository userAccountRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final HospitalDispatchAttemptRepository attemptRepository;
    private final HospitalOfferRepository offerRepository;
    private final RealtimeOutboxEventRepository outboxEventRepository;
    private final HospitalCommandFingerprint commandFingerprint;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TransportHospitalSearchResponse status(
            AuthenticatedAccount principal,
            String transportRequestId
    ) {
        UserAccount account = requireParamedic(principal, false);
        TransportRequest request = requireOwnedRequest(account, transportRequestId);
        HospitalDispatchAttempt attempt = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(transportRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        List<HospitalOffer> offers = offerRepository
                .findByTransportRequestPublicIdOrderByOfferedAtAsc(transportRequestId);
        return new TransportHospitalSearchResponse(
                request.getPublicId(),
                request.getStatus(),
                request.getCurrentDestinationOffer() == null
                        ? null
                        : request.getCurrentDestinationOffer().getPublicId(),
                toAttempt(attempt),
                exhaustionReason(request, offers),
                offers.stream().map(offer -> toOffer(request, offer)).toList(),
                clock.instant()
        );
    }

    @Transactional
    public DispatchAttemptCreationResult retry(
            AuthenticatedAccount principal,
            String transportRequestId,
            String requestedIdempotencyKey
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireParamedic(principal, true);
        TransportRequest scoped = requireOwnedRequest(account, transportRequestId);
        TransportRequest request = transportRequestRepository.findLockedById(scoped.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        byte[] fingerprint = commandFingerprint.retry(transportRequestId);
        HospitalDispatchAttempt existing = attemptRepository
                .findByTransportRequestPublicIdAndRetryIdempotencyKey(transportRequestId, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.hasSameRetryFingerprint(fingerprint)) {
                throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
            }
            return new DispatchAttemptCreationResult(toRetryResponse(request, existing, true), false);
        }
        if (request.getStatus() != TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }
        HospitalDispatchAttempt latest = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(transportRequestId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        Instant startedAt = clock.instant();
        request.resumeSearching();
        HospitalDispatchAttempt retry = HospitalDispatchAttempt.retry(
                request,
                latest.getAttemptNumber() + 1,
                idempotencyKey,
                fingerprint,
                startedAt
        );
        retry.scheduleNextExpansion(0, false, startedAt);
        attemptRepository.save(retry);
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.HOSPITAL_SEARCH_RETRY_STARTED,
                RealtimeAudienceType.ACCOUNT,
                account.getPublicId(),
                ATTEMPT_AGGREGATE,
                retry.getPublicId(),
                startedAt
        ));
        auditService.record(
                AuditAction.HOSPITAL_SEARCH_RETRY_STARTED,
                account,
                account.getOrganization(),
                ATTEMPT_AGGREGATE,
                retry.getPublicId(),
                startedAt
        );
        return new DispatchAttemptCreationResult(toRetryResponse(request, retry, false), true);
    }

    private UserAccount requireParamedic(AuthenticatedAccount principal, boolean lock) {
        if (principal.role() != UserRole.PARAMEDIC || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = (lock
                ? userAccountRepository.findLockedByPublicId(principal.accountId())
                : userAccountRepository.findByPublicId(principal.accountId()))
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return account;
    }

    private TransportRequest requireOwnedRequest(UserAccount account, String transportRequestId) {
        return transportRequestRepository.findByPublicId(transportRequestId)
                .filter(request -> request.getOwnerAccount().getPublicId().equals(account.getPublicId()))
                .filter(request -> request.getOrganization().getPublicId()
                        .equals(account.getOrganization().getPublicId()))
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
    }

    private TransportHospitalSearchResponse.Attempt toAttempt(HospitalDispatchAttempt attempt) {
        return new TransportHospitalSearchResponse.Attempt(
                attempt.getPublicId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getCurrentRadiusKm(),
                attempt.isCandidateShortage(),
                attempt.getNextExpansionAt(),
                attempt.getStartedAt(),
                attempt.getEndedAt()
        );
    }

    private TransportHospitalSearchResponse.Offer toOffer(
            TransportRequest request,
            HospitalOffer offer
    ) {
        boolean showContact = offer.getStatus() == HospitalOfferStatus.ACCEPTED
                || (request.getStatus() == TransportRequestStatus.CANDIDATES_EXHAUSTED
                && offer.getStatus() != HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
        return new TransportHospitalSearchResponse.Offer(
                offer.getPublicId(),
                offer.getDispatchAttempt().getAttemptNumber(),
                offer.getHospitalNameSnapshot(),
                showContact ? offer.getHospitalContactSnapshot() : null,
                offer.getStatus(),
                request.hasDestination(offer),
                offer.getStraightLineDistanceMeters(),
                offer.getRouteEstimateStatus(),
                offer.getRouteDistanceMeters(),
                offer.getEtaSeconds(),
                offer.getEtaCalculatedAt(),
                offer.getRejectionReason(),
                offer.getRejectionDetail(),
                offer.getWithdrawalReason(),
                offer.getWithdrawalDetail(),
                offer.getOfferedAt(),
                offer.getRespondedAt(),
                offer.getWithdrawnAt(),
                offer.getClosedAt()
        );
    }

    private String exhaustionReason(TransportRequest request, List<HospitalOffer> offers) {
        if (request.getStatus() != TransportRequestStatus.CANDIDATES_EXHAUSTED) {
            return null;
        }
        if (offers.isEmpty()) {
            return "NO_CANDIDATES";
        }
        if (offers.stream().anyMatch(offer -> offer.getStatus() == HospitalOfferStatus.NO_RESPONSE)) {
            return "NO_RESPONSE_INCLUDED";
        }
        return "ALL_REJECTED";
    }

    private DispatchAttemptResponse toRetryResponse(
            TransportRequest request,
            HospitalDispatchAttempt attempt,
            boolean replay
    ) {
        return new DispatchAttemptResponse(
                request.getPublicId(),
                request.getStatus(),
                attempt.getPublicId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getCurrentRadiusKm(),
                attempt.getNextExpansionAt(),
                replay
        );
    }
}
