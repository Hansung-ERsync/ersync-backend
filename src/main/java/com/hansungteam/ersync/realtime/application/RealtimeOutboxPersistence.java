package com.hansungteam.ersync.realtime.application;

import com.hansungteam.ersync.realtime.api.RealtimeEventResponse;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/** outbox 발행 전후 상태만 짧은 트랜잭션으로 변경합니다. */
@Service
@RequiredArgsConstructor
public class RealtimeOutboxPersistence {

    private final RealtimeOutboxEventRepository repository;

    @Transactional
    public RealtimeOutboxWork claim(Long eventId, Instant now, Instant leaseUntil) {
        RealtimeOutboxEvent event = repository.findLockedById(eventId).orElse(null);
        if (event == null
                || event.getPublishedAt() != null
                || event.getNextPublishAttemptAt().isAfter(now)) {
            return null;
        }
        event.reservePublish(leaseUntil);
        return new RealtimeOutboxWork(
                event.getId(),
                event.getAudienceType(),
                event.getAudiencePublicId(),
                new RealtimeEventResponse(
                        event.getPublicId(),
                        event.getEventType(),
                        event.getAggregateType(),
                        event.getAggregatePublicId(),
                        event.getOccurredAt()
                )
        );
    }

    @Transactional
    public void complete(Long eventId, Instant publishedAt) {
        repository.findLockedById(eventId).ifPresent(event -> event.markPublished(publishedAt));
    }

    @Transactional
    public void retry(Long eventId, Instant nextAttemptAt) {
        repository.findLockedById(eventId).ifPresent(event -> event.schedulePublishRetry(nextAttemptAt));
    }
}
