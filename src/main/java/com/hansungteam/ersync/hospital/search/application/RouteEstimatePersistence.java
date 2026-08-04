package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** 외부 HTTP 호출 전후의 ETA 상태를 각각 짧은 트랜잭션으로 저장합니다. */
@Service
@RequiredArgsConstructor
public class RouteEstimatePersistence {

    private static final String OFFER_AGGREGATE = "HOSPITAL_OFFER";

    private final HospitalOfferRepository offerRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final TransportCurrentLocationRepository locationRepository;
    private final RealtimeOutboxEventRepository outboxEventRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public RouteEstimateWork claim(Long offerId, Instant now, Instant leaseUntil) {
        HospitalOffer offer = lockOfferInRequestOrder(offerId);
        if (offer == null) {
            return null;
        }
        if (isObsoleteDynamicCalculation(offer)) {
            if (offer.getRouteEstimateStatus() == RouteEstimateStatus.CALCULATING) {
                offer.markRouteEstimateUnavailable();
            }
            return null;
        }
        if (offer.getRouteEstimateStatus() != RouteEstimateStatus.CALCULATING
                || offer.getEtaNextAttemptAt() == null
                || offer.getEtaNextAttemptAt().isAfter(now)) {
            return null;
        }
        offer.reserveRouteEstimate(leaseUntil);
        var request = offer.getTransportRequest();
        var currentLocation = request.hasDestination(offer)
                ? locationRepository.findByTransportRequestId(request.getId()).orElse(null)
                : null;
        return new RouteEstimateWork(
                offer.getId(),
                offer.getRouteEstimateGeneration(),
                offer.getEtaAttemptCount(),
                currentLocation == null
                        ? offer.getDispatchAttempt().getSearchOriginLatitude()
                        : currentLocation.getLatitude(),
                currentLocation == null
                        ? offer.getDispatchAttempt().getSearchOriginLongitude()
                        : currentLocation.getLongitude(),
                offer.getHospitalLatitudeSnapshot(),
                offer.getHospitalLongitudeSnapshot()
        );
    }

    @Transactional
    public void complete(Long offerId, long expectedGeneration, RouteEstimate estimate, Instant calculatedAt) {
        HospitalOffer offer = lockOfferInRequestOrder(offerId);
        if (!isCurrentCalculation(offer, expectedGeneration)) {
            return;
        }
        offer.completeRouteEstimate(estimate.distanceMeters(), estimate.etaSeconds(), calculatedAt);
        recordUpdatedEvents(offer, calculatedAt);
    }

    @Transactional
    public void retryOrFinish(
            Long offerId,
            long expectedGeneration,
            int maximumAttempts,
            Instant nextAttemptAt,
            Instant occurredAt
    ) {
        HospitalOffer offer = lockOfferInRequestOrder(offerId);
        if (!isCurrentCalculation(offer, expectedGeneration)) {
            return;
        }
        if (offer.getEtaAttemptCount() >= maximumAttempts) {
            offer.markRouteEstimateUnavailable();
            recordUpdatedEvents(offer, occurredAt);
        } else {
            offer.scheduleRouteEstimateRetry(nextAttemptAt);
        }
    }

    @Transactional
    public void finishUnavailable(Long offerId, long expectedGeneration, Instant occurredAt) {
        HospitalOffer offer = lockOfferInRequestOrder(offerId);
        if (!isCurrentCalculation(offer, expectedGeneration)) {
            return;
        }
        offer.markRouteEstimateUnavailable();
        recordUpdatedEvents(offer, occurredAt);
    }

    private boolean isCurrentCalculation(HospitalOffer offer, long expectedGeneration) {
        return offer != null
                && offer.getRouteEstimateStatus() == RouteEstimateStatus.CALCULATING
                && offer.getRouteEstimateGeneration() == expectedGeneration
                && !isObsoleteDynamicCalculation(offer);
    }

    private boolean isObsoleteDynamicCalculation(HospitalOffer offer) {
        return offer.getClosedAt() != null
                || offer.getTransportRequest().getStatus()
                        == com.hansungteam.ersync.transport.domain.TransportRequestStatus.COMPLETED
                || offer.getTransportRequest().getStatus()
                        == com.hansungteam.ersync.transport.domain.TransportRequestStatus.CANCELLED
                || (offer.getRouteEstimateGeneration() > 0
                        && !offer.getTransportRequest().hasDestination(offer));
    }

    private HospitalOffer lockOfferInRequestOrder(Long offerId) {
        Long requestId = offerRepository.findTransportRequestIdById(offerId).orElse(null);
        if (requestId == null || transportRequestRepository.findLockedById(requestId).isEmpty()) {
            return null;
        }
        HospitalOffer offer = offerRepository.findLockedById(offerId).orElse(null);
        if (offer != null) {
            entityManager.refresh(offer, LockModeType.PESSIMISTIC_WRITE);
        }
        return offer;
    }

    private void recordUpdatedEvents(HospitalOffer offer, Instant occurredAt) {
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.ETA_UPDATED,
                RealtimeAudienceType.ORGANIZATION,
                offer.getHospitalProfile().getOrganization().getPublicId(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                occurredAt
        ));
        outboxEventRepository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.ETA_UPDATED,
                RealtimeAudienceType.ACCOUNT,
                offer.getTransportRequest().getOwnerAccount().getPublicId(),
                OFFER_AGGREGATE,
                offer.getPublicId(),
                occurredAt
        ));
    }
}
