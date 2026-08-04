package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.hospital.search.application.DispatchAttemptCreationResult;
import com.hansungteam.ersync.hospital.search.application.TransportHospitalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 구급대원의 자기 요청 병원 탐색 현황·재전송 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests/{transportRequestId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportHospitalSearchController {

    private final TransportHospitalSearchService searchService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping("/hospital-search")
    public TransportHospitalSearchResponse status(@PathVariable String transportRequestId) {
        return searchService.status(currentAccountProvider.require(), transportRequestId);
    }

    @PostMapping("/dispatch-attempts")
    public ResponseEntity<DispatchAttemptResponse> retry(
            @PathVariable String transportRequestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        DispatchAttemptCreationResult result = searchService.retry(
                currentAccountProvider.require(),
                transportRequestId,
                idempotencyKey
        );
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create(
                "/api/v1/transport-requests/"
                        + transportRequestId
                        + "/dispatch-attempts/"
                        + result.response().dispatchAttemptId()
        )).body(result.response());
    }
}
