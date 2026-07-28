package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.hospital.application.HospitalProfileService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 병원 공용 계정의 자기 응급실 프로필과 수신 상태 API입니다.
 */
@RestController
@RequestMapping("/api/v1/hospital")
@PreAuthorize("hasRole('HOSPITAL_STAFF')")
public class HospitalProfileController {

    private final HospitalProfileService hospitalProfileService;

    public HospitalProfileController(HospitalProfileService hospitalProfileService) {
        this.hospitalProfileService = hospitalProfileService;
    }

    /**
     * 자기 병원의 응급실 프로필을 등록하거나 수정합니다.
     *
     * @param account 인증된 병원 공용 계정
     * @param request 응급실 주소, 좌표, 연락처
     * @return 저장된 병원 프로필
     */
    @PutMapping("/profile")
    public HospitalProfileResponse upsertProfile(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody UpsertHospitalProfileRequest request
    ) {
        return HospitalProfileResponse.from(hospitalProfileService.upsertProfile(
                account,
                request.erAddress(),
                request.latitude(),
                request.longitude(),
                request.erContact()
        ));
    }

    /**
     * 자기 병원의 응급실 프로필을 조회합니다.
     *
     * @param account 인증된 병원 공용 계정
     * @return 저장된 병원 프로필
     */
    @GetMapping("/profile")
    public HospitalProfileResponse getProfile(@AuthenticationPrincipal AuthenticatedAccount account) {
        return HospitalProfileResponse.from(hospitalProfileService.getProfile(account));
    }

    /**
     * 자기 병원의 새 요청 수신 상태를 변경합니다.
     *
     * @param account 인증된 병원 공용 계정
     * @param request 변경할 수신 상태
     * @return 변경된 병원 프로필
     */
    @PutMapping("/receiving-status")
    public HospitalProfileResponse updateReceivingStatus(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody UpdateReceivingStatusRequest request
    ) {
        return HospitalProfileResponse.from(hospitalProfileService.changeReceivingStatus(
                account,
                request.status()
        ));
    }
}
