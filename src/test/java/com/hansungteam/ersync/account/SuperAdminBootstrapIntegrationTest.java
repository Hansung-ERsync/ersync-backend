package com.hansungteam.ersync.account;

import com.hansungteam.ersync.account.application.SuperAdminBootstrap;
import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ersync.bootstrap.super-admin.enabled=true",
        "ersync.bootstrap.super-admin.login-id=superadmin",
        "ersync.bootstrap.super-admin.password=test-password"
})
@ActiveProfiles("test")
@Transactional
class SuperAdminBootstrapIntegrationTest {

    @Autowired
    private SuperAdminBootstrap superAdminBootstrap;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Test
    void createsOneHashedSuperAdminAndIsIdempotent() {
        UserAccount account = userAccountRepository.findByLoginIdAndRole(
                "superadmin",
                UserRole.SUPER_ADMIN
        ).orElseThrow();

        assertThat(account.getRole()).isEqualTo(UserRole.SUPER_ADMIN);
        assertThat(account.getOrganization()).isNull();
        assertThat(account.getPasswordHash()).isNotEqualTo("test-password");
        assertThat(passwordEncoder.matches("test-password", account.getPasswordHash())).isTrue();

        superAdminBootstrap.run(new DefaultApplicationArguments());

        assertThat(userAccountRepository.countByRole(UserRole.SUPER_ADMIN)).isEqualTo(1);
    }

    @Test
    void createsSuperAdminWhenAnotherRoleAlreadyUsesConfiguredLoginId() {
        userAccountRepository.deleteAll();
        userAccountRepository.flush();
        Organization emsUnit = organizationRepository.save(Organization.create(
                "관리자 동일 아이디 구급대",
                OrganizationType.EMS_UNIT
        ));
        userAccountRepository.saveAndFlush(UserAccount.createMember(
                emsUnit,
                "sharedbootstrap",
                "encoded-password",
                UserRole.PARAMEDIC
        ));

        ReflectionTestUtils.setField(superAdminBootstrap, "configuredLoginId", "sharedbootstrap");
        try {
            superAdminBootstrap.run(new DefaultApplicationArguments());
        } finally {
            ReflectionTestUtils.setField(superAdminBootstrap, "configuredLoginId", "superadmin");
        }

        assertThat(userAccountRepository.findByLoginIdAndRole("sharedbootstrap", UserRole.PARAMEDIC))
                .isPresent();
        assertThat(userAccountRepository.findByLoginIdAndRole("sharedbootstrap", UserRole.SUPER_ADMIN))
                .get()
                .extracting(UserAccount::getOrganization)
                .isNull();
        assertThat(userAccountRepository.countByRole(UserRole.SUPER_ADMIN)).isEqualTo(1L);
    }
}
