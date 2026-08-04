package com.hansungteam.ersync.realtime.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** SSE 연결 시에도 현재 계정·조직 활성 상태와 JWT 범위를 다시 확인합니다. */
@Service
@RequiredArgsConstructor
public class RealtimeSubscriptionService {

    private final UserAccountRepository userAccountRepository;
    private final RealtimeEventBroker broker;

    @Transactional(readOnly = true)
    public SseEmitter subscribe(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.PARAMEDIC && principal.role() != UserRole.HOSPITAL_STAFF) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != principal.role()
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return broker.subscribe(principal);
    }
}
