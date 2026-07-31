package com.hansungteam.ersync.account.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.security.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 런타임 Secret으로 최초 슈퍼 관리자 한 계정을 멱등 생성합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ersync.bootstrap.super-admin.enabled", havingValue = "true")
public class SuperAdminBootstrap implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ersync.bootstrap.super-admin.login-id:}")
    private String configuredLoginId;

    @Value("${ersync.bootstrap.super-admin.password:}")
    private String configuredPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userAccountRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            return;
        }

        String loginId = AccountCredentialPolicy.normalizeAndValidateLoginId(configuredLoginId);
        AccountCredentialPolicy.validatePassword(configuredPassword);
        if (userAccountRepository.existsByLoginId(loginId)) {
            throw new IllegalStateException("Configured super admin login ID is already used");
        }

        userAccountRepository.save(UserAccount.createSuperAdmin(
                loginId,
                passwordEncoder.encode(configuredPassword)
        ));
    }
}
