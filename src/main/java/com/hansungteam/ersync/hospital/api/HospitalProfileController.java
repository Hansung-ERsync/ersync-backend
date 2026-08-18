package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.hospital.application.HospitalProfileCommandService;
import com.hansungteam.ersync.hospital.application.HospitalProfileQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 병원 웹이 로그인 뒤 서버 상태를 복구하는 자기 병원 프로필 API입니다. */
@RestController
@RequestMapping("/api/v1/hospitals/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('HOSPITAL_STAFF')")
public class HospitalProfileController {

    private final HospitalProfileQueryService hospitalProfileQueryService;
    private final HospitalProfileCommandService hospitalProfileCommandService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping
    public HospitalProfileResponse getMine() {
        return hospitalProfileQueryService.getMine(currentAccountProvider.require());
    }

    @PutMapping
    public HospitalProfileResponse updateMine(
            @Valid @RequestBody UpdateHospitalProfileRequest request
    ) {
        return hospitalProfileCommandService.update(currentAccountProvider.require(), request);
    }
}
