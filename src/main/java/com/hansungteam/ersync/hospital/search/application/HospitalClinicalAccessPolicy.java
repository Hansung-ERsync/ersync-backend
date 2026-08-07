package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import org.springframework.stereotype.Component;

/** 병원에 현재 임상 원문을 공개할 수 있는지를 모든 조회 API에서 동일하게 판단합니다. */
@Component
public class HospitalClinicalAccessPolicy {

    public boolean canRead(HospitalOffer offer) {
        TransportRequest request = offer.getTransportRequest();
        if (request.getStatus() == TransportRequestStatus.COMPLETED
                || request.getStatus() == TransportRequestStatus.CANCELLED) {
            return false;
        }
        if (request.getCurrentDestinationOffer() != null) {
            return offer.getStatus() == HospitalOfferStatus.ACCEPTED && request.hasDestination(offer);
        }
        return offer.getStatus() == HospitalOfferStatus.PENDING
                || offer.getStatus() == HospitalOfferStatus.ACCEPTED;
    }
}
