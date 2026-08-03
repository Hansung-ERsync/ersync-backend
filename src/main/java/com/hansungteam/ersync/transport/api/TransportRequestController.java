package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.transport.application.TransportRequestCreationResult;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 구급대원이 최초 평가로 이송 요청을 생성하는 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportRequestController {

    private final TransportRequestService transportRequestService;
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
}
