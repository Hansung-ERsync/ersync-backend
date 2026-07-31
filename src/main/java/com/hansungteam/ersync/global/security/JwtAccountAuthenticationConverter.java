package com.hansungteam.ersync.global.security;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** JWT claim을 현재 DB 계정 상태와 대조해 서버 인증 주체로 변환합니다. */
@Component
@RequiredArgsConstructor
public class JwtAccountAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserAccountRepository userAccountRepository;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UserAccount account = userAccountRepository.findByPublicId(jwt.getSubject())
                .orElseThrow(() -> new InvalidBearerTokenException("Account does not exist"));
        if (!account.isActive()) {
            throw new InvalidBearerTokenException("Account is inactive");
        }

        UserRole tokenRole;
        try {
            tokenRole = UserRole.valueOf(jwt.getClaimAsString("role"));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new InvalidBearerTokenException("Role claim is invalid");
        }
        String accountOrganizationId = account.getOrganization() == null
                ? null
                : account.getOrganization().getPublicId();
        String tokenOrganizationId = jwt.getClaimAsString("organizationId");
        if (tokenRole != account.getRole() || !Objects.equals(tokenOrganizationId, accountOrganizationId)) {
            throw new InvalidBearerTokenException("Authorization claims are stale");
        }

        AuthenticatedAccount principal = new AuthenticatedAccount(
                account.getPublicId(),
                accountOrganizationId,
                account.getRole()
        );
        return new AccountJwtAuthenticationToken(principal, jwt);
    }
}
