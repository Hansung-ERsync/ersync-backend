package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.transport.application.TransportRequestCreationResult;
import com.hansungteam.ersync.transport.application.TransportRequestDetailQueryService;
import com.hansungteam.ersync.transport.application.TransportLifecycleService;
import com.hansungteam.ersync.transport.application.TransportRequestQueryService;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 구급대원이 최초 평가로 이송 요청을 생성하는 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportRequestController {

    private final TransportRequestService transportRequestService;
    private final TransportRequestDetailQueryService detailQueryService;
    private final TransportLifecycleService lifecycleService;
    private final TransportRequestQueryService queryService;
    private final CurrentAccountProvider currentAccountProvider;

    @PostMapping
    public ResponseEntity<CreateTransportRequestResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateTransportRequestRequest request
    ) {
        TransportRequestCreationResult result = transportRequestService.create(
                currentAccountProvider.require(),
                idempotencyKey,
                request
        );
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity
                .created(URI.create("/api/v1/transport-requests/" + result.response().transportRequestId()))
                .body(result.response());
    }

    @PostMapping("/{requestId}/cancel")
    public TransportCancellationResponse cancel(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CancelTransportRequestRequest request
    ) {
        return lifecycleService.cancel(
                currentAccountProvider.require(), requestId, idempotencyKey, request
        );
    }

    @PostMapping("/{requestId}/handoff-request")
    public TransportHandoffRequestResponse requestHandoff(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return lifecycleService.requestHandoff(
                currentAccountProvider.require(), requestId, idempotencyKey
        );
    }

    @GetMapping("/{requestId}")
    public TransportRequestDetailResponse detail(@PathVariable String requestId) {
        return detailQueryService.detail(currentAccountProvider.require(), requestId);
    }

    @GetMapping
    public TransportRequestListResponse list(
            @RequestParam(defaultValue = "ACTIVE") TransportRequestView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return queryService.list(currentAccountProvider.require(), view, page, size);
    }
}
