package com.hansungteam.ersync.realtime.application;

import com.hansungteam.ersync.realtime.api.RealtimeEventResponse;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;

/** 트랜잭션 밖 SSE 발행에 필요한 최소 outbox 데이터입니다. */
public record RealtimeOutboxWork(
        Long databaseId,
        RealtimeAudienceType audienceType,
        String audiencePublicId,
        RealtimeEventResponse event
) {
}
