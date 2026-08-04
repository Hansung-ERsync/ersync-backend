package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.transport.application.ClinicalUpdateResult;
import com.hansungteam.ersync.transport.application.TransportClinicalUpdateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** 구급대원이 활성 이송 중 새 임상 원본을 추가하는 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests/{requestId}/clinical-updates")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportClinicalUpdateController {

    private final TransportClinicalUpdateService updateService;
    private final CurrentAccountProvider currentAccountProvider;

    @PostMapping("/vital-signs")
    public ResponseEntity<ClinicalUpdateResponse> addVitalSigns(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateVitalSignsRequest request
    ) {
        return response(requestId, updateService.addVitalSigns(
                currentAccountProvider.require(), requestId, idempotencyKey, request
        ));
    }

    @PostMapping("/consciousness")
    public ResponseEntity<ClinicalUpdateResponse> addConsciousness(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateConsciousnessRequest request
    ) {
        return response(requestId, updateService.addConsciousness(
                currentAccountProvider.require(), requestId, idempotencyKey, request
        ));
    }

    @PostMapping("/pre-ktas")
    public ResponseEntity<ClinicalUpdateResponse> addPreKtas(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdatePreKtasRequest request
    ) {
        return response(requestId, updateService.addPreKtas(
                currentAccountProvider.require(), requestId, idempotencyKey, request
        ));
    }

    @PostMapping("/treatments")
    public ResponseEntity<ClinicalUpdateResponse> addTreatment(
            @PathVariable String requestId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody UpdateTreatmentRequest request
    ) {
        return response(requestId, updateService.addTreatment(
                currentAccountProvider.require(), requestId, idempotencyKey, request
        ));
    }

    private ResponseEntity<ClinicalUpdateResponse> response(String requestId, ClinicalUpdateResult result) {
        if (!result.created()) {
            return ResponseEntity.ok(result.response());
        }
        return ResponseEntity.created(URI.create(
                "/api/v1/transport-requests/" + requestId + "/clinical-updates/" + result.response().recordId()
        )).body(result.response());
    }
}
