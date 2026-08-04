package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/** 현재 목적지 유무와 제안 상태로 임상 갱신을 재조회할 병원 조직을 계산합니다. */
@Component
@RequiredArgsConstructor
public class ClinicalAudienceResolver {

    private final HospitalOfferRepository offerRepository;

    public Set<String> hospitalOrganizationIds(TransportRequest request) {
        if (request.getCurrentDestinationOffer() != null) {
            return Set.of(request.getCurrentDestinationOffer().getHospitalProfile()
                    .getOrganization().getPublicId());
        }
        Set<String> result = new LinkedHashSet<>();
        offerRepository.findByTransportRequestIdAndStatus(request.getId(), HospitalOfferStatus.PENDING).stream()
                .map(offer -> offer.getHospitalProfile().getOrganization().getPublicId())
                .forEach(result::add);
        offerRepository.findByTransportRequestIdAndStatus(request.getId(), HospitalOfferStatus.ACCEPTED).stream()
                .map(offer -> offer.getHospitalProfile().getOrganization().getPublicId())
                .forEach(result::add);
        return Set.copyOf(result);
    }
}
