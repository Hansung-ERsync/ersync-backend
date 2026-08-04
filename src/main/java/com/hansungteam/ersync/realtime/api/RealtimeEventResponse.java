package com.hansungteam.ersync.realtime.api;

import com.hansungteam.ersync.realtime.domain.RealtimeEventType;

import java.time.Instant;

/** 민감정보 없이 권위 API 재조회를 지시하는 실시간 갱신 신호입니다. */
public record RealtimeEventResponse(
        String eventId,
        RealtimeEventType type,
        String aggregateType,
        String aggregateId,
        Instant occurredAt
) {
}
