package com.hansungteam.ersync.realtime.application;

import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/** outbox 갱신 신호 발행과 SSE heartbeat를 주기적으로 실행합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ersync.realtime.scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RealtimeScheduler {

    private static final int BATCH_SIZE = 100;

    private final RealtimeOutboxEventRepository repository;
    private final RealtimeOutboxPublisher publisher;
    private final RealtimeEventBroker broker;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${ersync.realtime.outbox-fixed-delay:PT1S}")
    public void publishDueEvents() {
        List<Long> eventIds = repository.findDueIds(clock.instant(), PageRequest.of(0, BATCH_SIZE));
        for (Long eventId : eventIds) {
            publisher.publish(eventId);
        }
    }

    @Scheduled(fixedDelayString = "${ersync.realtime.heartbeat-delay:PT15S}")
    public void heartbeat() {
        broker.heartbeat();
    }
}
