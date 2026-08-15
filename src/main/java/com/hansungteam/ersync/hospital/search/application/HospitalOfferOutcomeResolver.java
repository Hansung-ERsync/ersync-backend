package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.api.HospitalOutcome;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 전역 이송 상태와 병원의 실제 응답을 병원별 화면 결과로 변환합니다. */
@Component
public class HospitalOfferOutcomeResolver {

    public HospitalOfferOutcomeResult resolve(HospitalOffer offer) {
        TransportRequest request = offer.getTransportRequest();
        return resolve(
                request.getStatus(),
                offer.getStatus(),
                request.hasDestination(offer),
                offer.getRespondedAt(),
                offer.getWithdrawnAt(),
                offer.getClosedAt(),
                request.getCompletedAt(),
                request.getCancelledAt()
        );
    }

    HospitalOfferOutcomeResult resolve(
            TransportRequestStatus requestStatus,
            HospitalOfferStatus offerStatus,
            boolean finalDestination,
            Instant respondedAt,
            Instant withdrawnAt,
            Instant closedAt,
            Instant completedAt,
            Instant cancelledAt
    ) {
        if (requestStatus == TransportRequestStatus.COMPLETED && finalDestination) {
            return result(HospitalOutcome.HANDOFF_COMPLETED_HERE, completedAt, closedAt);
        }
        if (offerStatus == HospitalOfferStatus.REJECTED) {
            return result(HospitalOutcome.REJECTED, respondedAt, closedAt);
        }
        if (offerStatus == HospitalOfferStatus.NO_RESPONSE) {
            return result(HospitalOutcome.NO_RESPONSE, closedAt);
        }
        if (offerStatus == HospitalOfferStatus.ACCEPTANCE_WITHDRAWN) {
            return result(HospitalOutcome.ACCEPTANCE_WITHDRAWN, withdrawnAt, closedAt);
        }
        if (requestStatus == TransportRequestStatus.COMPLETED) {
            return result(HospitalOutcome.COMPLETED_ELSEWHERE, completedAt, closedAt);
        }
        if (requestStatus == TransportRequestStatus.CANCELLED) {
            return result(HospitalOutcome.TRANSPORT_CANCELLED, cancelledAt, closedAt);
        }
        if (offerStatus == HospitalOfferStatus.PENDING) {
            return result(HospitalOutcome.AWAITING_RESPONSE, (Instant) null);
        }
        return result(HospitalOutcome.ACCEPTED, respondedAt);
    }

    private HospitalOfferOutcomeResult result(HospitalOutcome outcome, Instant... times) {
        for (Instant time : times) {
            if (time != null) {
                return new HospitalOfferOutcomeResult(outcome, time);
            }
        }
        return new HospitalOfferOutcomeResult(outcome, null);
    }
}
