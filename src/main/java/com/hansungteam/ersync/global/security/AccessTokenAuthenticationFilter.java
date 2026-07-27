package com.hansungteam.ersync.global.security;

import com.hansungteam.ersync.auth.application.JwtTokenProvider;
import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.auth.infrastructure.UserAccountEntity;
import com.hansungteam.ersync.auth.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Authorization Bearer 헤더의 Access Token을 검증하고 인증 컨텍스트를 구성합니다.
 */
@Component
@RequiredArgsConstructor
public class AccessTokenAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository userAccountRepository;
    private final SecurityErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedAccount tokenAccount = jwtTokenProvider.parse(authorization.substring(7), Instant.now());
            UserAccountEntity account = userAccountRepository.findWithOrganizationById(tokenAccount.accountId())
                    .orElseThrow(() -> new CustomException(ErrorCode.AUTH_ACCESS_TOKEN_INVALID));
            if (!account.active()) {
                throw new CustomException(ErrorCode.USER_INACTIVE);
            }

            AuthenticatedAccount principal = new AuthenticatedAccount(
                    account.id(),
                    account.organization() == null ? null : account.organization().id(),
                    account.role(),
                    account.loginId()
            );
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name()))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (CustomException ex) {
            SecurityContextHolder.clearContext();
            errorResponseWriter.write(response, ex.getErrorCode());
        }
    }
}
