package com.hansungteam.ersync.realtime.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.realtime.application.RealtimeSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Bearer 인증으로 연결하는 병원·구급대원 공통 SSE 갱신 신호 API입니다. */
@RestController
@RequestMapping("/api/v1/realtime/events")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PARAMEDIC', 'HOSPITAL_STAFF')")
public class RealtimeController {

    private final RealtimeSubscriptionService subscriptionService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(subscriptionService.subscribe(currentAccountProvider.require()));
    }
}
