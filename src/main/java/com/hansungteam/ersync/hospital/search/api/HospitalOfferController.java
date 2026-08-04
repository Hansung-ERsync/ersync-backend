package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.transport.api.ClinicalTimelineResponse;
import com.hansungteam.ersync.transport.api.TransportLocationResponse;
import com.hansungteam.ersync.transport.application.ClinicalTimelineQueryService;
import com.hansungteam.ersync.transport.application.TransportLocationService;
import com.hansungteam.ersync.transport.application.TransportLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 병원 공용 계정의 자기 조직 제안 조회·수락·거절 API입니다. */
@RestController
@RequestMapping("/api/v1/hospitals/me/offers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOSPITAL_STAFF')")
public class HospitalOfferController {

    private final HospitalOfferService hospitalOfferService;
    private final ClinicalTimelineQueryService clinicalTimelineQueryService;
    private final TransportLocationService transportLocationService;
    private final TransportLifecycleService transportLifecycleService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping
    public HospitalOfferListResponse list(
            @RequestParam(defaultValue = "ACTIVE") HospitalOfferView view,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return hospitalOfferService.list(currentAccountProvider.require(), view, page, size);
    }

    @GetMapping("/{offerId}")
    public HospitalOfferDetailResponse detail(@PathVariable String offerId) {
        return hospitalOfferService.detail(currentAccountProvider.require(), offerId);
    }

    @GetMapping("/{offerId}/clinical-timeline")
    public ClinicalTimelineResponse clinicalTimeline(
            @PathVariable String offerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return clinicalTimelineQueryService.hospitalTimeline(
                currentAccountProvider.require(), offerId, page, size
        );
    }

    @GetMapping("/{offerId}/location")
    public TransportLocationResponse location(@PathVariable String offerId) {
        return transportLocationService.hospitalLocation(currentAccountProvider.require(), offerId);
    }

    @PostMapping("/{offerId}/accept")
    public HospitalOfferDecisionResponse accept(
            @PathVariable String offerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return hospitalOfferService.accept(currentAccountProvider.require(), offerId, idempotencyKey);
    }

    @PostMapping("/{offerId}/reject")
    public HospitalOfferDecisionResponse reject(
            @PathVariable String offerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RejectHospitalOfferRequest request
    ) {
        return hospitalOfferService.reject(
                currentAccountProvider.require(),
                offerId,
                idempotencyKey,
                request
        );
    }

    @PostMapping("/{offerId}/withdraw-acceptance")
    public HospitalAcceptanceWithdrawalResponse withdrawAcceptance(
            @PathVariable String offerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody WithdrawHospitalAcceptanceRequest request
    ) {
        return hospitalOfferService.withdrawAcceptance(
                currentAccountProvider.require(),
                offerId,
                idempotencyKey,
                request
        );
    }

    @PostMapping("/{offerId}/confirm-handoff")
    public HospitalHandoffConfirmationResponse confirmHandoff(
            @PathVariable String offerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        return transportLifecycleService.confirmHandoff(
                currentAccountProvider.require(), offerId, idempotencyKey
        );
    }
}
