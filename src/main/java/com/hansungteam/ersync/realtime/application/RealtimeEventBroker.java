package com.hansungteam.ersync.realtime.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.realtime.api.RealtimeEventResponse;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 현재 서버 인스턴스의 인증된 SSE 연결에 최소 갱신 신호를 전달합니다. */
@Component
public class RealtimeEventBroker {

    private static final long CONNECTION_TIMEOUT_MILLIS = Duration.ofMinutes(14).toMillis();

    private final ConcurrentHashMap<AudienceKey, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(AuthenticatedAccount account) {
        AudienceKey key = audienceKey(account);
        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT_MILLIS);
        Set<SseEmitter> emitters = subscribers.computeIfAbsent(
                key,
                ignored -> ConcurrentHashMap.newKeySet()
        );
        emitters.add(emitter);
        Runnable cleanup = () -> remove(key, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ignored -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("connected").comment("state must be fetched from API"));
        } catch (IOException exception) {
            cleanup.run();
            emitter.completeWithError(exception);
        }
        return emitter;
    }

    public void publish(
            RealtimeAudienceType audienceType,
            String audiencePublicId,
            RealtimeEventResponse event
    ) {
        AudienceKey key = new AudienceKey(audienceType, audiencePublicId);
        Set<SseEmitter> emitters = subscribers.getOrDefault(key, Set.of());
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(event.eventId())
                        .name("update")
                        .data(event));
            } catch (IOException | IllegalStateException exception) {
                remove(key, emitter);
                emitter.complete();
            }
        }
    }

    public void heartbeat() {
        subscribers.forEach((key, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException exception) {
                    remove(key, emitter);
                    emitter.complete();
                }
            }
        });
    }

    public int subscriberCount() {
        return subscribers.values().stream().mapToInt(Set::size).sum();
    }

    private AudienceKey audienceKey(AuthenticatedAccount account) {
        if (account.role() == UserRole.PARAMEDIC) {
            return new AudienceKey(RealtimeAudienceType.ACCOUNT, account.accountId());
        }
        if (account.role() == UserRole.HOSPITAL_STAFF && account.organizationId() != null) {
            return new AudienceKey(RealtimeAudienceType.ORGANIZATION, account.organizationId());
        }
        throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
    }

    private void remove(AudienceKey key, SseEmitter emitter) {
        Set<SseEmitter> emitters = subscribers.get(key);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            subscribers.remove(key, emitters);
        }
    }

    private record AudienceKey(RealtimeAudienceType type, String publicId) {
    }
}
