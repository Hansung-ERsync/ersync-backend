package com.hansungteam.ersync.realtime.infrastructure;

import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 트랜잭션 outbox 영속성 접근점입니다. */
public interface RealtimeOutboxEventRepository extends JpaRepository<RealtimeOutboxEvent, Long> {

    long countByEventType(com.hansungteam.ersync.realtime.domain.RealtimeEventType eventType);

    @Query("select event.id from RealtimeOutboxEvent event "
            + "where event.publishedAt is null "
            + "and event.nextPublishAttemptAt <= :now order by event.occurredAt asc")
    List<Long> findDueIds(@Param("now") Instant now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from RealtimeOutboxEvent event where event.id = :id")
    Optional<RealtimeOutboxEvent> findLockedById(@Param("id") Long id);
}
