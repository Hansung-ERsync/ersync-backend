package com.hansungteam.ersync.global.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

/** JWT와 현재 계정 상태를 모두 검증한 인증 객체입니다. */
public final class AccountJwtAuthenticationToken extends AbstractAuthenticationToken {

    private final AuthenticatedAccount principal;
    private final Jwt jwt;

    public AccountJwtAuthenticationToken(AuthenticatedAccount principal, Jwt jwt) {
        super(principal.authorities());
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt.getTokenValue();
    }

    @Override
    public AuthenticatedAccount getPrincipal() {
        return principal;
    }
}
