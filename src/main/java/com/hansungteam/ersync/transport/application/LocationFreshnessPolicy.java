package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.domain.LocationFreshness;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** DB 상태 변경 없이 마지막 서버 수신 시각과 현재 시각으로 freshness를 계산합니다. */
@Component
public class LocationFreshnessPolicy {

    private final Duration staleAfter;

    public LocationFreshnessPolicy(@Value("${ersync.location.stale-after:PT30S}") Duration staleAfter) {
        if (staleAfter.isNegative() || staleAfter.isZero()) {
            throw new IllegalArgumentException("Location stale-after must be positive");
        }
        this.staleAfter = staleAfter;
    }

    public LocationFreshness freshness(Instant lastReceivedAt, Instant now) {
        if (lastReceivedAt == null) {
            return LocationFreshness.NOT_RECEIVED;
        }
        Duration age = age(lastReceivedAt, now);
        return age.compareTo(staleAfter) >= 0 ? LocationFreshness.STALE : LocationFreshness.CURRENT;
    }

    public long ageSeconds(Instant lastReceivedAt, Instant now) {
        return age(lastReceivedAt, now).toSeconds();
    }

    private Duration age(Instant lastReceivedAt, Instant now) {
        if (now.isBefore(lastReceivedAt)) {
            return Duration.ZERO;
        }
        return Duration.between(lastReceivedAt, now);
    }
}
