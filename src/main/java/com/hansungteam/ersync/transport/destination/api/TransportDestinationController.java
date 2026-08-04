package com.hansungteam.ersync.transport.destination.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구급대원이 자기 이송 요청의 수락 병원을 목적지로 선택·변경하는 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests/{transportRequestId}/destination")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportDestinationController {

    private final TransportDestinationService destinationService;
    private final CurrentAccountProvider currentAccountProvider;

    @PostMapping
    public TransportDestinationResponse select(
            @PathVariable String transportRequestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SelectTransportDestinationRequest request
    ) {
        return TransportDestinationResponse.from(destinationService.select(
                currentAccountProvider.require(),
                transportRequestId,
                idempotencyKey,
                request.offerId()
        ));
    }
}
