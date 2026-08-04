package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
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
    private final RealtimeOutboxEventRepository outboxEventRepository;

    @Transactional
    public RouteEstimateWork claim(Long offerId, Instant now, Instant leaseUntil) {
        HospitalOffer offer = offerRepository.findLockedById(offerId).orElse(null);
        if (offer == null
                || offer.getRouteEstimateStatus() != RouteEstimateStatus.CALCULATING
                || offer.getEtaNextAttemptAt() == null
                || offer.getEtaNextAttemptAt().isAfter(now)) {
            return null;
        }
        offer.reserveRouteEstimate(leaseUntil);
        return new RouteEstimateWork(
                offer.getId(),
                offer.getEtaAttemptCount(),
                offer.getTransportRequest().getOriginLatitude(),
                offer.getTransportRequest().getOriginLongitude(),
                offer.getHospitalLatitudeSnapshot(),
                offer.getHospitalLongitudeSnapshot()
        );
    }

    @Transactional
    public void complete(Long offerId, RouteEstimate estimate, Instant calculatedAt) {
        HospitalOffer offer = offerRepository.findLockedById(offerId).orElse(null);
        if (offer == null || offer.getRouteEstimateStatus() != RouteEstimateStatus.CALCULATING) {
            return;
        }
        offer.completeRouteEstimate(estimate.distanceMeters(), estimate.etaSeconds(), calculatedAt);
        recordUpdatedEvents(offer, calculatedAt);
    }

    @Transactional
    public void retryOrFinish(
            Long offerId,
            int maximumAttempts,
            Instant nextAttemptAt,
            Instant occurredAt
    ) {
        HospitalOffer offer = offerRepository.findLockedById(offerId).orElse(null);
        if (offer == null || offer.getRouteEstimateStatus() != RouteEstimateStatus.CALCULATING) {
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
    public void finishUnavailable(Long offerId, Instant occurredAt) {
        HospitalOffer offer = offerRepository.findLockedById(offerId).orElse(null);
        if (offer == null || offer.getRouteEstimateStatus() != RouteEstimateStatus.CALCULATING) {
            return;
        }
        offer.markRouteEstimateUnavailable();
        recordUpdatedEvents(offer, occurredAt);
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
