package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;

import java.math.BigDecimal;
import java.time.Instant;

/** 병원 웹의 로그인 복구와 계정 화면에 사용하는 자기 병원 프로필입니다. */
public record HospitalProfileResponse(
        String loginId,
        String organizationName,
        String hospitalId,
        String address,
        String detailAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String contact,
        ReceivingStatus receivingStatus,
        Instant updatedAt
) {

    public static HospitalProfileResponse from(UserAccount account, HospitalProfile profile) {
        return new HospitalProfileResponse(
                account.getLoginId(),
                account.getOrganization().getName(),
                profile.getPublicId(),
                profile.getAddress(),
                profile.getDetailAddress(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getContact(),
                profile.getReceivingStatus(),
                profile.getUpdatedAt()
        );
    }
}
