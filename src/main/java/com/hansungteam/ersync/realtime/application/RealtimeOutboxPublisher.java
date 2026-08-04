package com.hansungteam.ersync.realtime.application;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** outbox를 claim한 뒤 DB 트랜잭션 밖에서 SSE broker로 발행합니다. */
@Service
@RequiredArgsConstructor
public class RealtimeOutboxPublisher {

    private final RealtimeOutboxPersistence persistence;
    private final RealtimeEventBroker broker;
    private final Clock clock;

    @Value("${ersync.realtime.outbox-claim-lease:PT10S}")
    private Duration claimLease;

    @Value("${ersync.realtime.outbox-retry-delay:PT3S}")
    private Duration retryDelay;

    public void publish(Long eventId) {
        Instant now = clock.instant();
        RealtimeOutboxWork work = persistence.claim(eventId, now, now.plus(claimLease));
        if (work == null) {
            return;
        }
        try {
            broker.publish(work.audienceType(), work.audiencePublicId(), work.event());
            persistence.complete(work.databaseId(), clock.instant());
        } catch (RuntimeException exception) {
            persistence.retry(work.databaseId(), clock.instant().plus(retryDelay));
        }
    }
}
