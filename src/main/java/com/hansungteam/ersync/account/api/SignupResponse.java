package com.hansungteam.ersync.account.api;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;

/** 가입 후 프론트가 로그인 화면으로 이동하는 데 필요한 계정 범위입니다. */
public record SignupResponse(
        String accountId,
        String organizationId,
        String organizationName,
        UserRole role,
        String hospitalId,
        ReceivingStatus receivingStatus
) {

    public static SignupResponse paramedic(UserAccount account) {
        return new SignupResponse(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                account.getOrganization().getName(),
                account.getRole(),
                null,
                null
        );
    }

    public static SignupResponse hospital(UserAccount account, HospitalProfile profile) {
        return new SignupResponse(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                account.getOrganization().getName(),
                account.getRole(),
                profile.getPublicId(),
                profile.getReceivingStatus()
        );
    }
}
