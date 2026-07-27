package com.hansungteam.ersync.auth.domain;

import com.hansungteam.ersync.global.security.UserRole;

/**
 * 인증된 요청에서 사용할 계정 식별자와 권한 범위입니다.
 */
public record AuthenticatedAccount(
        String accountId,
        String organizationId,
        UserRole role,
        String loginId
) {
}
