package com.hansungteam.ersync.account;

import com.hansungteam.ersync.account.application.SuperAdminBootstrap;
import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.security.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ersync.bootstrap.super-admin.enabled=true",
        "ersync.bootstrap.super-admin.login-id=superadmin",
        "ersync.bootstrap.super-admin.password=test-password"
})
@ActiveProfiles("test")
class SuperAdminBootstrapIntegrationTest {

    @Autowired
    private SuperAdminBootstrap superAdminBootstrap;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsOneHashedSuperAdminAndIsIdempotent() {
        UserAccount account = userAccountRepository.findByLoginId("superadmin").orElseThrow();

        assertThat(account.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(account.getOrganization()).isNull();
        assertThat(account.getPasswordHash()).isNotEqualTo("test-password");
        assertThat(passwordEncoder.matches("test-password", account.getPasswordHash())).isTrue();

        superAdminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userAccountRepository.countByRole(UserRole.SUPER_ADMIN)).isEqualTo(1);
    }
}
