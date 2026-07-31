package com.hansungteam.ersync.global.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.List;

/** 검증된 토큰에서 만든 서버 인증 주체입니다. */
public record AuthenticatedAccount(
        String accountId,
        String organizationId,
        UserRole role
) implements Principal {

    @Override
    public String getName() {
        return accountId;
    }

    public List<SimpleGrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
