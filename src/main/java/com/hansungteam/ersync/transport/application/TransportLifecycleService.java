package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.api.HospitalHandoffConfirmationResponse;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.api.CancelTransportRequestRequest;
import com.hansungteam.ersync.transport.api.TransportCancellationResponse;
import com.hansungteam.ersync.transport.api.TransportHandoffRequestResponse;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportLifecycleCommand;
import com.hansungteam.ersync.transport.domain.TransportLifecycleCommandType;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportLifecycleCommandRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 요청 잠금을 기준으로 취소와 양측 인계 확인을 멱등하게 직렬화합니다. */
@Service
@RequiredArgsConstructor
public class TransportLifecycleService {

    private static final String LIFECYCLE_AGGREGATE = "TRANSPORT_LIFECYCLE";

    private final UserAccountRepository accountRepository;
    private final TransportRequestRepository requestRepository;
    private final TransportLifecycleCommandRepository commandRepository;
    private final HospitalDispatchAttemptRepository attemptRepository;
    private final HospitalOfferRepository offerRepository;
    private final RealtimeOutboxEventRepository outboxRepository;
    private final AuditService auditService;
    private final TransportLifecycleFingerprint fingerprint;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TransportCancellationResponse cancel(
            AuthenticatedAccount principal,
            String requestId,
            String requestedIdempotencyKey,
            CancelTransportRequestRequest input
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        String detail = normalizeCancellationDetail(input.reason(), input.detail());
        UserAccount account = requireParamedicAccount(principal);
        TransportRequest request = lockOwnedRequest(requestId, account);
        byte[] requestFingerprint = fingerprint.cancel(requestId, input.reason(), detail);
        TransportLifecycleCommand replay = replayOrNull(
                request, idempotencyKey, requestFingerprint, TransportLifecycleCommandType.CANCEL
        );
        if (replay != null) {
            return cancellationResponse(replay, true);
        }
        if (!isCancellable(request.getStatus())) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }

        Instant occurredAt = clock.instant();
        HospitalOffer previousDestination = request.getCurrentDestinationOffer();
        stopSearchingAttempts(request, occurredAt);
        List<HospitalOffer> offers = lockAllOffers(request);
        Set<String> affectedHospitals = activeHospitalOrganizations(offers);
        offers.forEach(offer -> offer.close(occurredAt));
        request.cancel(account, input.reason(), detail, occurredAt);
        TransportLifecycleCommand command = commandRepository.save(TransportLifecycleCommand.cancel(
                request,
                account,
                previousDestination,
                input.reason(),
                detail,
                idempotencyKey,
                requestFingerprint,
                occurredAt
        ));
        recordSignals(
                command,
                RealtimeEventType.TRANSPORT_CANCELLED,
                AuditAction.TRANSPORT_CANCELLED,
                account,
                request.getOwnerAccount().getPublicId(),
                affectedHospitals,
                occurredAt
        );
        return cancellationResponse(command, false);
    }

    @Transactional
    public TransportHandoffRequestResponse requestHandoff(
            AuthenticatedAccount principal,
            String requestId,
            String requestedIdempotencyKey
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireParamedicAccount(principal);
        TransportRequest request = lockOwnedRequest(requestId, account);
        byte[] requestFingerprint = fingerprint.handoffRequest(requestId);
        TransportLifecycleCommand replay = replayOrNull(
                request, idempotencyKey, requestFingerprint, TransportLifecycleCommandType.HANDOFF_REQUEST
        );
        if (replay != null) {
            return handoffRequestResponse(replay, true);
        }
        if (request.getStatus() != TransportRequestStatus.EN_ROUTE
                || request.getCurrentDestinationOffer() == null) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }

        HospitalOffer destination = lockOffer(request.getCurrentDestinationOffer().getId());
        if (!request.hasDestination(destination)
                || destination.getStatus() != HospitalOfferStatus.ACCEPTED
                || destination.getClosedAt() != null) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }
        Instant occurredAt = clock.instant();
        request.requestHandoff(account, occurredAt);
        TransportLifecycleCommand command = commandRepository.save(TransportLifecycleCommand.handoffRequest(
                request,
                account,
                destination,
                idempotencyKey,
                requestFingerprint,
                occurredAt
        ));
        recordSignals(
                command,
                RealtimeEventType.HANDOFF_REQUESTED,
                AuditAction.HANDOFF_REQUESTED,
                account,
                request.getOwnerAccount().getPublicId(),
                Set.of(destination.getHospitalProfile().getOrganization().getPublicId()),
                occurredAt
        );
        return handoffRequestResponse(command, false);
    }

    @Transactional
    public HospitalHandoffConfirmationResponse confirmHandoff(
            AuthenticatedAccount principal,
            String offerId,
            String requestedIdempotencyKey
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireHospitalAccount(principal);
        HospitalOfferRepository.HospitalOfferLockScope scope = offerRepository
                .findLockScope(offerId, principal.organizationId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        TransportRequest request = requestRepository.findLockedById(scope.getTransportRequestId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        byte[] requestFingerprint = fingerprint.handoffConfirm(request.getPublicId(), offerId);
        TransportLifecycleCommand replay = replayOrNull(
                request, idempotencyKey, requestFingerprint, TransportLifecycleCommandType.HANDOFF_CONFIRM
        );
        if (replay != null) {
            return handoffConfirmationResponse(replay, true);
        }

        attemptRepository.findLockedById(scope.getDispatchAttemptId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        List<HospitalOffer> offers = lockAllOffers(request);
        HospitalOffer destination = offers.stream()
                .filter(offer -> offer.getId().equals(scope.getOfferId()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        if (request.getCurrentDestinationOffer() == null
                || !request.hasDestination(destination)
                || !destination.getHospitalProfile().getOrganization().getId()
                        .equals(account.getOrganization().getId())) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND);
        }
        if (request.getStatus() != TransportRequestStatus.HANDOFF_REQUESTED
                || destination.getStatus() != HospitalOfferStatus.ACCEPTED
                || destination.getClosedAt() != null) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }

        Instant occurredAt = clock.instant();
        Set<String> affectedHospitals = allHospitalOrganizations(offers);
        request.confirmHandoff(account, occurredAt);
        offers.forEach(offer -> offer.close(occurredAt));
        TransportLifecycleCommand command = commandRepository.save(TransportLifecycleCommand.handoffConfirm(
                request,
                account,
                destination,
                idempotencyKey,
                requestFingerprint,
                occurredAt
        ));
        recordSignals(
                command,
                RealtimeEventType.HANDOFF_COMPLETED,
                AuditAction.HANDOFF_CONFIRMED,
                account,
                request.getOwnerAccount().getPublicId(),
                affectedHospitals,
                occurredAt
        );
        return handoffConfirmationResponse(command, false);
    }

    private UserAccount requireParamedicAccount(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.PARAMEDIC || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = requireActiveLockedAccount(principal);
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private UserAccount requireHospitalAccount(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.HOSPITAL_STAFF || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = requireActiveLockedAccount(principal);
        if (account.getRole() != UserRole.HOSPITAL_STAFF
                || account.getOrganization().getType() != OrganizationType.HOSPITAL) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return account;
    }

    private UserAccount requireActiveLockedAccount(AuthenticatedAccount principal) {
        UserAccount account = accountRepository.findLockedByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getOrganization() == null
                || !account.getOrganization().isActive()
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private TransportRequest lockOwnedRequest(String requestId, UserAccount account) {
        TransportRequest request = requestRepository
                .findLockedOwnedByPublicId(requestId, account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        if (!request.getOrganization().getId().equals(account.getOrganization().getId())) {
            throw new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND);
        }
        return request;
    }

    private TransportLifecycleCommand replayOrNull(
            TransportRequest request,
            String idempotencyKey,
            byte[] requestFingerprint,
            TransportLifecycleCommandType expectedType
    ) {
        TransportLifecycleCommand existing = commandRepository
                .findByTransportRequestIdAndIdempotencyKey(request.getId(), idempotencyKey)
                .orElse(null);
        if (existing == null) {
            return null;
        }
        if (existing.getCommandType() != expectedType || !existing.hasSameFingerprint(requestFingerprint)) {
            throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
        }
        return existing;
    }

    private void stopSearchingAttempts(TransportRequest request, Instant occurredAt) {
        for (Long attemptId : attemptRepository.findIdsByTransportRequestIdAndStatusOrderById(
                request.getId(), HospitalDispatchAttemptStatus.SEARCHING
        )) {
            HospitalDispatchAttempt attempt = attemptRepository.findLockedById(attemptId)
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
            attempt.stopOnCancellation(occurredAt);
        }
    }

    private List<HospitalOffer> lockAllOffers(TransportRequest request) {
        List<HospitalOffer> result = new ArrayList<>();
        for (Long offerId : offerRepository.findIdsByTransportRequestIdOrderById(request.getId())) {
            result.add(lockOffer(offerId));
        }
        return result;
    }

    private HospitalOffer lockOffer(Long offerId) {
        HospitalOffer offer = offerRepository.findLockedById(offerId)
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        entityManager.refresh(offer, LockModeType.PESSIMISTIC_WRITE);
        return offer;
    }

    private Set<String> activeHospitalOrganizations(List<HospitalOffer> offers) {
        Set<String> result = new LinkedHashSet<>();
        offers.stream()
                .filter(offer -> offer.getClosedAt() == null)
                .filter(offer -> offer.getStatus() == HospitalOfferStatus.PENDING
                        || offer.getStatus() == HospitalOfferStatus.ACCEPTED)
                .map(offer -> offer.getHospitalProfile().getOrganization().getPublicId())
                .forEach(result::add);
        return result;
    }

    private Set<String> allHospitalOrganizations(List<HospitalOffer> offers) {
        Set<String> result = new LinkedHashSet<>();
        offers.stream()
                .map(offer -> offer.getHospitalProfile().getOrganization().getPublicId())
                .forEach(result::add);
        return result;
    }

    private void recordSignals(
            TransportLifecycleCommand command,
            RealtimeEventType eventType,
            AuditAction action,
            UserAccount actor,
            String ownerAccountId,
            Set<String> hospitalOrganizationIds,
            Instant occurredAt
    ) {
        outboxRepository.save(RealtimeOutboxEvent.create(
                eventType,
                RealtimeAudienceType.ACCOUNT,
                ownerAccountId,
                LIFECYCLE_AGGREGATE,
                command.getPublicId(),
                occurredAt
        ));
        for (String organizationId : hospitalOrganizationIds) {
            outboxRepository.save(RealtimeOutboxEvent.create(
                    eventType,
                    RealtimeAudienceType.ORGANIZATION,
                    organizationId,
                    LIFECYCLE_AGGREGATE,
                    command.getPublicId(),
                    occurredAt
            ));
        }
        auditService.record(
                action,
                actor,
                actor.getOrganization(),
                LIFECYCLE_AGGREGATE,
                command.getPublicId(),
                occurredAt
        );
    }

    private String normalizeCancellationDetail(TransportCancellationReason reason, String requestedDetail) {
        if (reason == null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        if (reason != TransportCancellationReason.OTHER) {
            if (requestedDetail != null) {
                throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
            }
            return null;
        }
        String detail = requestedDetail == null ? null : requestedDetail.trim();
        if (detail == null || detail.isEmpty() || detail.length() > 200) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return detail;
    }

    private boolean isCancellable(TransportRequestStatus status) {
        return status == TransportRequestStatus.SEARCHING
                || status == TransportRequestStatus.CANDIDATES_EXHAUSTED
                || status == TransportRequestStatus.ACCEPTED_AVAILABLE
                || status == TransportRequestStatus.EN_ROUTE;
    }

    private TransportCancellationResponse cancellationResponse(
            TransportLifecycleCommand command,
            boolean replay
    ) {
        return new TransportCancellationResponse(
                command.getTransportRequest().getPublicId(),
                command.getResultingRequestStatus(),
                command.getCancellationReason(),
                command.getCancellationDetail(),
                command.getOccurredAt(),
                replay
        );
    }

    private TransportHandoffRequestResponse handoffRequestResponse(
            TransportLifecycleCommand command,
            boolean replay
    ) {
        HospitalOffer destination = command.getDestinationOffer();
        return new TransportHandoffRequestResponse(
                command.getTransportRequest().getPublicId(),
                command.getResultingRequestStatus(),
                destination.getPublicId(),
                destination.getHospitalNameSnapshot(),
                command.getOccurredAt(),
                replay
        );
    }

    private HospitalHandoffConfirmationResponse handoffConfirmationResponse(
            TransportLifecycleCommand command,
            boolean replay
    ) {
        return new HospitalHandoffConfirmationResponse(
                command.getDestinationOffer().getPublicId(),
                command.getTransportRequest().getPublicId(),
                command.getResultingRequestStatus(),
                command.getOccurredAt(),
                replay
        );
    }
}
