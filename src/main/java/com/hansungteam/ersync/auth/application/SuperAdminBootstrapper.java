package com.hansungteam.ersync.auth.application;

import com.hansungteam.ersync.auth.infrastructure.UserAccountEntity;
import com.hansungteam.ersync.auth.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.security.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * 운영자가 환경 설정으로 지정한 최초 슈퍼 관리자 계정을 생성합니다.
 */
@Slf4j
@Component
public class SuperAdminBootstrapper implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String loginId;
    private final String password;

    public SuperAdminBootstrapper(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock,
            @Value("${ersync.bootstrap.super-admin.login-id:}") String loginId,
            @Value("${ersync.bootstrap.super-admin.password:}") String password
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.loginId = loginId;
        this.password = password;
    }

    /**
     * 설정이 있고 기존 슈퍼 관리자가 없을 때만 계정을 생성합니다.
     *
     * @param args 애플리케이션 시작 인자
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (loginId.isBlank() || password.isBlank() || userAccountRepository.existsByRole(UserRole.SUPER_ADMIN)) {
            return;
        }

        userAccountRepository.save(new UserAccountEntity(
                null,
                UserRole.SUPER_ADMIN,
                loginId,
                passwordEncoder.encode(password),
                clock.instant()
        ));
        log.info("event=SUPER_ADMIN_BOOTSTRAPPED loginId={}", loginId);
    }
}
