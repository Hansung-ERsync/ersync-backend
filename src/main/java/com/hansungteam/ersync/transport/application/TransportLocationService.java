package com.hansungteam.ersync.transport.application;

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
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.api.TransportLocationResponse;
import com.hansungteam.ersync.transport.api.UpdateTransportLocationRequest;
import com.hansungteam.ersync.transport.domain.LocationFreshness;
import com.hansungteam.ersync.transport.domain.TransportCurrentLocation;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.domain.TransportUpdateCommand;
import com.hansungteam.ersync.transport.domain.TransportUpdateCommandType;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportUpdateCommandRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** 요청별 최신 위치 한 행을 멱등하게 갱신하고 현재 권한에 따라 조회합니다. */
@Service
@RequiredArgsConstructor
public class TransportLocationService {

    private static final Set<TransportRequestStatus> UPDATABLE_STATUSES = EnumSet.of(
            TransportRequestStatus.SEARCHING,
            TransportRequestStatus.CANDIDATES_EXHAUSTED,
            TransportRequestStatus.ACCEPTED_AVAILABLE,
            TransportRequestStatus.EN_ROUTE
    );

    private final UserAccountRepository userAccountRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final HospitalOfferRepository hospitalOfferRepository;
    private final TransportCurrentLocationRepository locationRepository;
    private final TransportUpdateCommandRepository commandRepository;
    private final TransportUpdateFingerprint updateFingerprint;
    private final LocationFreshnessPolicy freshnessPolicy;
    private final RealtimeOutboxEventRepository outboxEventRepository;
    private final AuditService auditService;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public TransportLocationResponse update(
            AuthenticatedAccount authenticated,
            String requestId,
            String requestedIdempotencyKey,
            UpdateTransportLocationRequest input
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireParamedic(authenticated, true);
        TransportRequest request = transportRequestRepository
                .findLockedOwnedByPublicId(requestId, account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        byte[] fingerprint = updateFingerprint.digest(TransportUpdateCommandType.LOCATION.name(), input);
        TransportUpdateCommand existing = commandRepository
                .findByTransportRequestIdAndIdempotencyKey(request.getId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (existing.getCommandType() != TransportUpdateCommandType.LOCATION
                    || !existing.hasSameFingerprint(fingerprint)) {
                throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
            }
            TransportCurrentLocation stored = locationRepository.findByTransportRequestId(request.getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
            return response(request, stored, existing.getLocationReplaced(), true, clock.instant());
        }
        if (!UPDATABLE_STATUSES.contains(request.getStatus())) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }

        Instant receivedAt = clock.instant();
        TransportCurrentLocation location = locationRepository.findByTransportRequestId(request.getId()).orElse(null);
        boolean replaced;
        if (location == null) {
            location = TransportCurrentLocation.create(
                    request, input.latitude(), input.longitude(), input.capturedAt(), receivedAt
            );
            replaced = true;
        } else {
            replaced = location.replaceIfCurrent(
                    input.latitude(), input.longitude(), input.capturedAt(), receivedAt
            );
        }
        locationRepository.save(location);
        if (replaced && request.getCurrentDestinationOffer() != null) {
            HospitalOffer destination = hospitalOfferRepository
                    .findLockedById(request.getCurrentDestinationOffer().getId())
                    .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
            entityManager.refresh(destination, LockModeType.PESSIMISTIC_WRITE);
            destination.scheduleRouteEstimateRecalculation(receivedAt);
        }
        commandRepository.save(TransportUpdateCommand.location(
                request, idempotencyKey, fingerprint, location.getPublicId(), replaced, receivedAt
        ));
        recordSignals(account, request, location, replaced, receivedAt);
        return response(request, location, replaced, false, receivedAt);
    }

    @Transactional(readOnly = true)
    public TransportLocationResponse ownerLocation(AuthenticatedAccount authenticated, String requestId) {
        UserAccount account = requireParamedic(authenticated, false);
        TransportRequest request = transportRequestRepository
                .findByPublicIdAndOwnerAccountPublicId(requestId, account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        TransportCurrentLocation location = locationRepository.findByTransportRequestId(request.getId()).orElse(null);
        return response(request, location, null, false, clock.instant());
    }

    @Transactional(readOnly = true)
    public TransportLocationResponse hospitalLocation(AuthenticatedAccount authenticated, String offerId) {
        UserAccount account = requireHospital(authenticated);
        HospitalProfile profile = hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .filter(found -> found.getOrganization().getPublicId().equals(authenticated.organizationId()))
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        HospitalOffer offer = hospitalOfferRepository
                .findByPublicIdAndHospitalProfileOrganizationPublicId(
                        offerId, profile.getOrganization().getPublicId()
                )
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND));
        TransportRequest request = offer.getTransportRequest();
        if (offer.getStatus() != HospitalOfferStatus.ACCEPTED
                || request.getCurrentDestinationOffer() == null
                || !request.hasDestination(offer)
                || request.getStatus() == TransportRequestStatus.COMPLETED
                || request.getStatus() == TransportRequestStatus.CANCELLED) {
            throw new CustomException(ErrorCode.HOSPITAL_OFFER_NOT_FOUND);
        }
        TransportCurrentLocation location = locationRepository.findByTransportRequestId(request.getId()).orElse(null);
        return response(request, location, null, false, clock.instant());
    }

    private UserAccount requireParamedic(AuthenticatedAccount authenticated, boolean lock) {
        if (authenticated.role() != UserRole.PARAMEDIC || authenticated.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = (lock
                ? userAccountRepository.findLockedByPublicId(authenticated.accountId())
                : userAccountRepository.findByPublicId(authenticated.accountId()))
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT
                || !account.getOrganization().getPublicId().equals(authenticated.organizationId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private UserAccount requireHospital(AuthenticatedAccount authenticated) {
        if (authenticated.role() != UserRole.HOSPITAL_STAFF || authenticated.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.HOSPITAL_STAFF
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.HOSPITAL
                || !account.getOrganization().getPublicId().equals(authenticated.organizationId())) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return account;
    }

    private TransportLocationResponse response(
            TransportRequest request,
            TransportCurrentLocation location,
            Boolean replaced,
            boolean replay,
            Instant now
    ) {
        if (location == null) {
            return new TransportLocationResponse(
                    request.getPublicId(), null, null, null, null, LocationFreshness.NOT_RECEIVED,
                    null, now, replaced, null, null, null, null, null, null, null, replay
            );
        }
        HospitalOffer destination = request.getCurrentDestinationOffer();
        return new TransportLocationResponse(
                request.getPublicId(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCapturedAt(),
                location.getLastReceivedAt(),
                freshnessPolicy.freshness(location.getLastReceivedAt(), now),
                freshnessPolicy.ageSeconds(location.getLastReceivedAt(), now),
                now,
                replaced,
                destination == null ? null : destination.getRouteEstimateStatus(),
                destination == null ? null : destination.getRouteDistanceMeters(),
                destination == null ? null : destination.getEtaSeconds(),
                destination == null ? null : destination.getEtaCalculatedAt(),
                destination == null ? null : destination.getLastSuccessRouteDistanceMeters(),
                destination == null ? null : destination.getLastSuccessEtaSeconds(),
                destination == null ? null : destination.getLastSuccessEtaCalculatedAt(),
                replay
        );
    }

    private void recordSignals(
            UserAccount account,
            TransportRequest request,
            TransportCurrentLocation location,
            boolean replaced,
            Instant receivedAt
    ) {
        auditService.record(
                AuditAction.AMBULANCE_LOCATION_UPDATED,
                account,
                account.getOrganization(),
                "TRANSPORT_CURRENT_LOCATION",
                location.getPublicId(),
                receivedAt
        );
        if (!replaced) {
            return;
        }
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.AMBULANCE_LOCATION_UPDATED,
                RealtimeAudienceType.ACCOUNT,
                account.getPublicId(),
                "TRANSPORT_REQUEST",
                request.getPublicId(),
                receivedAt
        ));
        if (request.getCurrentDestinationOffer() != null) {
            outboxEventRepository.save(RealtimeOutboxEvent.create(
                    RealtimeEventType.AMBULANCE_LOCATION_UPDATED,
                    RealtimeAudienceType.ORGANIZATION,
                    request.getCurrentDestinationOffer().getHospitalProfile().getOrganization().getPublicId(),
                    "TRANSPORT_REQUEST",
                    request.getPublicId(),
                    receivedAt
            ));
        }
    }
}
