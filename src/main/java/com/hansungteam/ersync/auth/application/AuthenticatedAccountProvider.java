package com.hansungteam.ersync.auth.application;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * application 계층에서 현재 인증 계정을 조회할 수 있게 합니다.
 */
@Component
public class AuthenticatedAccountProvider {

    public AuthenticatedAccount current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            throw new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
        }
        return account;
    }
}
