package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import org.springframework.stereotype.Component;

/** 병원에 현재 또는 동결된 임상 원문을 공개할 수 있는지를 동일하게 판단합니다. */
@Component
public class HospitalClinicalAccessPolicy {

    public boolean canRead(HospitalOffer offer) {
        TransportRequest request = offer.getTransportRequest();
        if (request.getStatus() == TransportRequestStatus.COMPLETED
                || request.getStatus() == TransportRequestStatus.CANCELLED) {
            return false;
        }
        boolean activeOffer = offer.getStatus() == HospitalOfferStatus.PENDING
                || offer.getStatus() == HospitalOfferStatus.ACCEPTED;
        if (!activeOffer) {
            return false;
        }
        if (request.getCurrentDestinationOffer() == null || request.hasDestination(offer)) {
            return true;
        }
        return offer.getClinicalVisibilityCutoffAt() != null
                && offer.getFrozenLastClinicalUpdateAt() != null;
    }
}
