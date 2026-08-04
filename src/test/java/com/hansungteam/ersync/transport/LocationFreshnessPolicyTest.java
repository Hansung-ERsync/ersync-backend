package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.transport.application.LocationFreshnessPolicy;
import com.hansungteam.ersync.transport.domain.LocationFreshness;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LocationFreshnessPolicyTest {

    private final LocationFreshnessPolicy policy = new LocationFreshnessPolicy(Duration.ofSeconds(30));

    @Test
    void thirtySecondBoundaryAndFutureClockSkewAreDeterministic() {
        Instant receivedAt = Instant.parse("2026-08-04T01:00:00Z");

        assertThat(policy.freshness(null, receivedAt)).isEqualTo(LocationFreshness.NOT_RECEIVED);
        assertThat(policy.freshness(receivedAt, receivedAt.plusMillis(29_999)))
                .isEqualTo(LocationFreshness.CURRENT);
        assertThat(policy.freshness(receivedAt, receivedAt.plusSeconds(30)))
                .isEqualTo(LocationFreshness.STALE);
        assertThat(policy.ageSeconds(receivedAt, receivedAt.minusSeconds(1))).isZero();
    }
}
