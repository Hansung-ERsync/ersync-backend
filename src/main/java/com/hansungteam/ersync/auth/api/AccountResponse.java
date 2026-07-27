package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.auth.application.AuthService;
import com.hansungteam.ersync.global.security.UserRole;

/**
 * 인증 계정 정보 응답 DTO입니다.
 */
public record AccountResponse(
        String accountId,
        String organizationId,
        UserRole role,
        String loginId
) {

    public static AccountResponse from(AuthService.AccountResult result) {
        return new AccountResponse(
                result.accountId(),
                result.organizationId(),
                result.role(),
                result.loginId()
        );
    }
}
