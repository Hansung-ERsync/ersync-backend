package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/** 계산 대기 중인 병원 제안을 제한된 묶음으로 ETA coordinator에 전달합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ersync.maps.naver.eta-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RouteEstimateScheduler {

    private static final int BATCH_SIZE = 50;

    private final HospitalOfferRepository offerRepository;
    private final RouteEstimateCoordinator coordinator;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ersync.maps.naver.eta-scheduler-fixed-delay:PT1S}")
    public void processDueEstimates() {
        List<Long> offerIds = offerRepository.findRouteEstimateDueIds(
                clock.instant(),
                PageRequest.of(0, BATCH_SIZE)
        );
        for (Long offerId : offerIds) {
            coordinator.process(offerId);
        }
    }
}
