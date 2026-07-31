package com.hansungteam.ersync.global.security;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** 현재 요청에서 서버가 검증한 계정 주체만 반환합니다. */
@Component
public class CurrentAccountProvider {

    public AuthenticatedAccount require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount account)) {
            throw new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED);
        }
        return account;
    }
}
