package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.auth.application.AuthService;
import com.hansungteam.ersync.global.security.UserRole;

/**
 * 회원가입 완료 응답 DTO입니다.
 */
public record SignupResponse(
        String accountId,
        String organizationId,
        UserRole role,
        String loginId
) {

    public static SignupResponse from(AuthService.SignupResult result) {
        return new SignupResponse(
                result.accountId(),
                result.organizationId(),
                result.role(),
                result.loginId()
        );
    }
}
