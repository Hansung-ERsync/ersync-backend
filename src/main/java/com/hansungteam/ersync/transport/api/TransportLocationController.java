package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.transport.application.TransportLocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구급대원이 자기 요청의 최신 위치 한 건을 갱신·조회하는 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests/{requestId}/location")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportLocationController {

    private final TransportLocationService locationService;
    private final CurrentAccountProvider currentAccountProvider;

    @PutMapping
    public TransportLocationResponse update(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateTransportLocationRequest request
    ) {
        return locationService.update(currentAccountProvider.require(), requestId, idempotencyKey, request);
    }

    @GetMapping
    public TransportLocationResponse get(@PathVariable String requestId) {
        return locationService.ownerLocation(currentAccountProvider.require(), requestId);
    }
}
