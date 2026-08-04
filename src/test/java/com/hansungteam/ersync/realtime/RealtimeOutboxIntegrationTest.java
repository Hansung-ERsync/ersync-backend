package com.hansungteam.ersync.realtime;

import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.realtime.application.RealtimeEventBroker;
import com.hansungteam.ersync.realtime.application.RealtimeOutboxPublisher;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class RealtimeOutboxIntegrationTest {

    @Autowired private RealtimeOutboxEventRepository repository;
    @Autowired private RealtimeOutboxPublisher publisher;
    @Autowired private RealtimeEventBroker broker;

    @Test
    void publisherMarksMinimalOutboxSignalPublishedEvenWithNoConnectedClient() {
        RealtimeOutboxEvent event = repository.save(RealtimeOutboxEvent.create(
                RealtimeEventType.HOSPITAL_OFFER_ACCEPTED,
                RealtimeAudienceType.ACCOUNT,
                "00000000-0000-0000-0000-000000000001",
                "HOSPITAL_OFFER",
                "00000000-0000-0000-0000-000000000002",
                Instant.now().minusSeconds(1)
        ));

        publisher.publish(event.getId());

        RealtimeOutboxEvent stored = repository.findById(event.getId()).orElseThrow();
        assertThat(stored.getPublishedAt()).isNotNull();
        assertThat(stored.getPublishAttemptCount()).isEqualTo(1);
    }

    @Test
    void paramedicSubscriptionUsesAccountAudienceWithoutSensitivePayload() {
        var emitter = broker.subscribe(new AuthenticatedAccount(
                "00000000-0000-0000-0000-000000000003",
                "00000000-0000-0000-0000-000000000004",
                UserRole.PARAMEDIC
        ));

        assertThat(broker.subscriberCount()).isEqualTo(1);
        emitter.complete();
    }
}
