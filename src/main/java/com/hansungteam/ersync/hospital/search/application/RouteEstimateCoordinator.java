package com.hansungteam.ersync.hospital.search.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** DB 트랜잭션 밖에서 네이버를 호출하고 성공·실패 결과만 다시 저장합니다. */
@Service
@RequiredArgsConstructor
public class RouteEstimateCoordinator {

    private final RouteEstimatePersistence persistence;
    private final RouteEstimateProvider provider;
    private final Clock clock;

    @Value("${ersync.maps.naver.maximum-attempts:3}")
    private int maximumAttempts;

    @Value("${ersync.maps.naver.retry-delay:PT5S}")
    private Duration retryDelay;

    @Value("${ersync.maps.naver.claim-lease:PT15S}")
    private Duration claimLease;

    public void process(Long offerId) {
        Instant now = clock.instant();
        RouteEstimateWork work = persistence.claim(offerId, now, now.plus(claimLease));
        if (work == null) {
            return;
        }
        try {
            RouteEstimate estimate = provider.estimate(
                    work.originLatitude(),
                    work.originLongitude(),
                    work.destinationLatitude(),
                    work.destinationLongitude()
            );
            persistence.complete(work.offerId(), estimate, clock.instant());
        } catch (TemporaryRouteEstimateException exception) {
            Instant failedAt = clock.instant();
            persistence.retryOrFinish(
                    work.offerId(),
                    maximumAttempts,
                    failedAt.plus(retryDelay),
                    failedAt
            );
        } catch (PermanentRouteEstimateException exception) {
            persistence.finishUnavailable(work.offerId(), clock.instant());
        }
    }
}
