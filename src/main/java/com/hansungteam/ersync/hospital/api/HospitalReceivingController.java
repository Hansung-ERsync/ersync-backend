package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.hospital.application.HospitalReceivingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 병원 공용 계정 전용 응급실 수신 상태 API입니다. */
@RestController
@RequestMapping("/api/v1/hospitals/me/receiving-status")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOSPITAL_STAFF')")
public class HospitalReceivingController {

    private final HospitalReceivingService hospitalReceivingService;
    private final CurrentAccountProvider currentAccountProvider;

    @PutMapping
    public HospitalReceivingStatusResponse change(
            @Valid @RequestBody ChangeReceivingStatusRequest request
    ) {
        return hospitalReceivingService.change(currentAccountProvider.require(), request.status());
    }
}
