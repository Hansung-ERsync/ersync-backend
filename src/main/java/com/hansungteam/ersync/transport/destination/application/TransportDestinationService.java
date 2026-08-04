package com.hansungteam.ersync.transport.destination.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptTrigger;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.application.IdempotencyKeyPolicy;
import com.hansungteam.ersync.transport.destination.domain.TransportDestinationCommand;
import com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType;
import com.hansungteam.ersync.transport.destination.infrastructure.TransportDestinationCommandRepository;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import lombok.RequiredArgsConstructor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 구급대원 소유 요청의 목적지를 직렬화하고 명령 결과를 멱등하게 보존합니다. */
@Service
@RequiredArgsConstructor
public class TransportDestinationService {

    private static final String DESTINATION_AGGREGATE = "TRANSPORT_DESTINATION";

    private final UserAccountRepository userAccountRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final HospitalOfferRepository hospitalOfferRepository;
    private final HospitalDispatchAttemptRepository attemptRepository;
    private final TransportDestinationCommandRepository commandRepository;
    private final TransportCurrentLocationRepository currentLocationRepository;
    private final RealtimeOutboxEventRepository outboxEventRepository;
    private final AuditService auditService;
    private final TransportDestinationFingerprint fingerprint;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TransportDestinationSelectionResult select(
            AuthenticatedAccount principal,
            String transportRequestId,
            String requestedIdempotencyKey,
            String destinationOfferId
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireParamedicAccount(principal);
        TransportRequest request = transportRequestRepository
                .findLockedOwnedByPublicId(transportRequestId, account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        entityManager.refresh(request, LockModeType.PESSIMISTIC_WRITE);
        verifyOwnership(request, account);

        byte[] requestFingerprint = fingerprint.digest(transportRequestId, destinationOfferId);
        TransportDestinationCommand existing = commandRepository
                .findByTransportRequestIdAndIdempotencyKey(request.getId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.hasSameFingerprint(requestFingerprint)) {
                throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
            }
            return toResult(existing, true);
        }

        if (request.getStatus() != TransportRequestStatus.ACCEPTED_AVAILABLE
                && request.getStatus() != TransportRequestStatus.EN_ROUTE) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }

        HospitalDispatchAttempt activeRecovery = lockActiveRecovery(request);

        Long destinationOfferPk = hospitalOfferRepository
                .findIdByPublicIdAndTransportRequestId(destinationOfferId, request.getId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_DESTINATION_NOT_ACCEPTED));
        HospitalOffer destination = hospitalOfferRepository.findLockedById(destinationOfferPk)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_DESTINATION_NOT_ACCEPTED));
        entityManager.refresh(destination, LockModeType.PESSIMISTIC_WRITE);
        if (!destination.getTransportRequest().getId().equals(request.getId())
                || destination.getStatus() != HospitalOfferStatus.ACCEPTED) {
            throw new CustomException(ErrorCode.TRANSPORT_DESTINATION_NOT_ACCEPTED);
        }

        HospitalOffer previous = request.getCurrentDestinationOffer();
        TransportDestinationResultType resultType = determineResult(previous, destination);
        if (resultType != TransportDestinationResultType.UNCHANGED) {
            request.selectDestination(destination);
        }

        Instant changedAt = clock.instant();
        if (resultType != TransportDestinationResultType.UNCHANGED
                && currentLocationRepository.findByTransportRequestId(request.getId()).isPresent()) {
            destination.scheduleRouteEstimateRecalculation(changedAt);
        }
        if (activeRecovery != null) {
            activeRecovery.stopOnDestination(changedAt);
        }
        TransportDestinationCommand command = commandRepository.save(TransportDestinationCommand.record(
                request,
                previous,
                destination,
                resultType,
                account,
                account.getOrganization(),
                idempotencyKey,
                requestFingerprint,
                changedAt
        ));
        if (resultType != TransportDestinationResultType.UNCHANGED) {
            recordChange(command, account, previous, destination, changedAt);
        }
        return toResult(command, false);
    }

    private UserAccount requireParamedicAccount(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.PARAMEDIC || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findLockedByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private void verifyOwnership(TransportRequest request, UserAccount account) {
        if (!request.getOwnerAccount().getId().equals(account.getId())
                || !request.getOrganization().getId().equals(account.getOrganization().getId())) {
            throw new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND);
        }
    }

    private TransportDestinationResultType determineResult(HospitalOffer previous, HospitalOffer destination) {
        if (previous == null) {
            return TransportDestinationResultType.SELECTED;
        }
        if (previous.getId().equals(destination.getId())) {
            return TransportDestinationResultType.UNCHANGED;
        }
        return TransportDestinationResultType.CHANGED;
    }

    private HospitalDispatchAttempt lockActiveRecovery(TransportRequest request) {
        List<Long> activeIds = attemptRepository.findLatestIdsByTransportRequestIdAndStatus(
                request.getId(), HospitalDispatchAttemptStatus.SEARCHING, PageRequest.of(0, 1)
        );
        if (activeIds.isEmpty()) {
            return null;
        }
        HospitalDispatchAttempt locked = attemptRepository.findLockedById(activeIds.getFirst()).orElse(null);
        if (locked == null
                || locked.getTriggerType() != HospitalDispatchAttemptTrigger.ACCEPTANCE_WITHDRAWAL
                || locked.getStatus() != HospitalDispatchAttemptStatus.SEARCHING) {
            return null;
        }
        return locked;
    }

    private void recordChange(
            TransportDestinationCommand command,
            UserAccount account,
            HospitalOffer previous,
            HospitalOffer destination,
            Instant changedAt
    ) {
        RealtimeEventType eventType = command.getResultType() == TransportDestinationResultType.SELECTED
                ? RealtimeEventType.DESTINATION_SELECTED
                : RealtimeEventType.DESTINATION_CHANGED;
        AuditAction auditAction = command.getResultType() == TransportDestinationResultType.SELECTED
                ? AuditAction.DESTINATION_SELECTED
                : AuditAction.DESTINATION_CHANGED;

        outboxEventRepository.save(RealtimeOutboxEvent.create(
                eventType,
                RealtimeAudienceType.ACCOUNT,
                account.getPublicId(),
                DESTINATION_AGGREGATE,
                command.getPublicId(),
                changedAt
        ));
        for (String organizationId : affectedHospitalOrganizations(
                command.getTransportRequest(), command.getResultType(), previous, destination
        )) {
            outboxEventRepository.save(RealtimeOutboxEvent.create(
                    eventType,
                    RealtimeAudienceType.ORGANIZATION,
                    organizationId,
                    DESTINATION_AGGREGATE,
                    command.getPublicId(),
                    changedAt
            ));
        }
        auditService.record(
                auditAction,
                account,
                account.getOrganization(),
                DESTINATION_AGGREGATE,
                command.getPublicId(),
                changedAt
        );
    }

    private Set<String> affectedHospitalOrganizations(
            TransportRequest request,
            TransportDestinationResultType resultType,
            HospitalOffer previous,
            HospitalOffer destination
    ) {
        Set<String> organizationIds = new LinkedHashSet<>();
        if (resultType == TransportDestinationResultType.SELECTED) {
            hospitalOfferRepository.findByTransportRequestIdAndStatus(request.getId(), HospitalOfferStatus.ACCEPTED)
                    .stream()
                    .map(offer -> offer.getHospitalProfile().getOrganization().getPublicId())
                    .forEach(organizationIds::add);
            return organizationIds;
        }
        if (previous != null) {
            organizationIds.add(previous.getHospitalProfile().getOrganization().getPublicId());
        }
        organizationIds.add(destination.getHospitalProfile().getOrganization().getPublicId());
        return organizationIds;
    }

    private TransportDestinationSelectionResult toResult(
            TransportDestinationCommand command,
            boolean replay
    ) {
        return new TransportDestinationSelectionResult(
                command.getTransportRequest().getPublicId(),
                command.getResultingRequestStatus(),
                command.getDestinationOffer().getPublicId(),
                command.getPreviousDestinationOffer() == null
                        ? null
                        : command.getPreviousDestinationOffer().getPublicId(),
                command.getResultType(),
                command.getOccurredAt(),
                replay
        );
    }
}
