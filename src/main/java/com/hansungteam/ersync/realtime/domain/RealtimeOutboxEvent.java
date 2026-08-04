package com.hansungteam.ersync.realtime.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 상태 변경과 같은 트랜잭션에 저장하는 민감정보 없는 알림 발행 의도입니다. */
@Entity
@Table(name = "realtime_outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RealtimeOutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private RealtimeEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private RealtimeAudienceType audienceType;

    @Column(name = "audience_public_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String audiencePublicId;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_public_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String aggregatePublicId;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant occurredAt;

    @Column(name = "publish_attempt_count", nullable = false)
    private int publishAttemptCount;

    @Column(name = "next_publish_attempt_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant nextPublishAttemptAt;

    @Column(name = "published_at", columnDefinition = "datetime(6)")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    private RealtimeOutboxEvent(
            RealtimeEventType eventType,
            RealtimeAudienceType audienceType,
            String audiencePublicId,
            String aggregateType,
            String aggregatePublicId,
            Instant occurredAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.eventType = eventType;
        this.audienceType = audienceType;
        this.audiencePublicId = audiencePublicId;
        this.aggregateType = aggregateType;
        this.aggregatePublicId = aggregatePublicId;
        this.occurredAt = occurredAt;
        this.nextPublishAttemptAt = occurredAt;
        this.createdAt = occurredAt;
    }

    public static RealtimeOutboxEvent create(
            RealtimeEventType eventType,
            RealtimeAudienceType audienceType,
            String audiencePublicId,
            String aggregateType,
            String aggregatePublicId,
            Instant occurredAt
    ) {
        return new RealtimeOutboxEvent(
                eventType,
                audienceType,
                audiencePublicId,
                aggregateType,
                aggregatePublicId,
                occurredAt
        );
    }

    /** 발행 전에 짧은 lease를 잡아 같은 이벤트의 동시 발행을 줄입니다. */
    public void reservePublish(Instant leaseUntil) {
        if (publishedAt != null) {
            return;
        }
        publishAttemptCount++;
        nextPublishAttemptAt = leaseUntil;
    }

    public void markPublished(Instant publishedAt) {
        if (this.publishedAt == null) {
            this.publishedAt = publishedAt;
        }
    }

    public void schedulePublishRetry(Instant nextAttemptAt) {
        if (publishedAt == null) {
            nextPublishAttemptAt = nextAttemptAt;
        }
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
        if (nextPublishAttemptAt == null) {
            nextPublishAttemptAt = occurredAt;
        }
    }
}
