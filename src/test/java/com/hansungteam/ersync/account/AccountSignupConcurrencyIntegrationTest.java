package com.hansungteam.ersync.account;

import com.hansungteam.ersync.account.api.HospitalSignupRequest;
import com.hansungteam.ersync.account.api.ParamedicSignupRequest;
import com.hansungteam.ersync.account.application.AccountSignupService;
import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AccountSignupConcurrencyIntegrationTest {

    @Autowired
    private AccountSignupService accountSignupService;

    @Autowired
    private InvitationService invitationService;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private InvitationCodeRepository invitationCodeRepository;

    @Autowired
    private HospitalProfileRepository hospitalProfileRepository;

    @Test
    void simultaneousUseOfOneInvitationCreatesOnlyOneParamedicAccount() throws Exception {
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "concurrentadmin1",
                "encoded-password"
        ));
        Organization ems = organizationRepository.save(Organization.create(
                "동시성 구급대 1",
                OrganizationType.EMS_UNIT
        ));
        var issued = invitationService.issue(
                admin.getPublicId(),
                new IssueInvitationRequest(
                        ems.getPublicId(),
                        UserRole.PARAMEDIC,
                        InvitationExpiryOption.THREE_DAYS,
                        null
                )
        );

        List<AttemptResult> results = runTogether(
                () -> signupParamedic(issued.code(), "concurrentmedic1"),
                () -> signupParamedic(issued.code(), "concurrentmedic2")
        );

        assertThat(results).filteredOn(AttemptResult::success).hasSize(1);
        assertThat(results)
                .filteredOn(result -> "INVITATION_003".equals(result.errorCode()))
                .hasSize(1);
        assertThat(userAccountRepository.existsByLoginIdAndRole("concurrentmedic1", UserRole.PARAMEDIC)
                ^ userAccountRepository.existsByLoginIdAndRole("concurrentmedic2", UserRole.PARAMEDIC))
                .isTrue();
        assertThat(invitationCodeRepository.findByPublicId(issued.invitation().invitationCodeId()).orElseThrow()
                .getStatus()).isEqualTo(InvitationStatus.USED);
    }

    @Test
    void simultaneousHospitalSignupsWithDifferentCodesCreateOneSharedAccount() throws Exception {
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "concurrentadmin2",
                "encoded-password"
        ));
        Organization hospital = organizationRepository.save(Organization.create(
                "동시성 병원 2",
                OrganizationType.HOSPITAL
        ));
        String firstCode = issueHospitalCode(admin, hospital);
        String secondCode = issueHospitalCode(admin, hospital);

        List<AttemptResult> results = runTogether(
                () -> signupHospital(firstCode, "concurrenthospital1", hospital.getName()),
                () -> signupHospital(secondCode, "concurrenthospital2", hospital.getName())
        );

        assertThat(results).filteredOn(AttemptResult::success).hasSize(1);
        assertThat(results)
                .filteredOn(result -> "USER_004".equals(result.errorCode()))
                .hasSize(1);
        assertThat(hospitalProfileRepository.existsByOrganizationPublicId(hospital.getPublicId())).isTrue();
        assertThat(userAccountRepository.existsByLoginIdAndRole(
                        "concurrenthospital1",
                        UserRole.HOSPITAL_STAFF
                ) ^ userAccountRepository.existsByLoginIdAndRole(
                        "concurrenthospital2",
                        UserRole.HOSPITAL_STAFF
                )).isTrue();
    }

    @Test
    void simultaneousParamedicSignupsWithSameLoginIdCreateOneAccount() throws Exception {
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "concurrentadmin3",
                "encoded-password"
        ));
        Organization firstEms = organizationRepository.save(Organization.create(
                "동일 역할 구급대 1",
                OrganizationType.EMS_UNIT
        ));
        Organization secondEms = organizationRepository.save(Organization.create(
                "동일 역할 구급대 2",
                OrganizationType.EMS_UNIT
        ));
        String firstCode = issueCode(admin, firstEms, UserRole.PARAMEDIC);
        String secondCode = issueCode(admin, secondEms, UserRole.PARAMEDIC);

        List<AttemptResult> results = runTogether(
                () -> signupParamedic(firstCode, "samerolelogin"),
                () -> signupParamedic(secondCode, "samerolelogin")
        );

        assertThat(results).filteredOn(AttemptResult::success).hasSize(1);
        assertThat(results)
                .filteredOn(result -> "USER_003".equals(result.errorCode()))
                .hasSize(1);
        assertThat(userAccountRepository.findByLoginIdAndRole("samerolelogin", UserRole.PARAMEDIC))
                .isPresent();
    }

    @Test
    void simultaneousDifferentRoleSignupsCanShareOneLoginId() throws Exception {
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "concurrentadmin4",
                "encoded-password"
        ));
        Organization ems = organizationRepository.save(Organization.create(
                "교차 역할 구급대",
                OrganizationType.EMS_UNIT
        ));
        Organization hospital = organizationRepository.save(Organization.create(
                "교차 역할 병원",
                OrganizationType.HOSPITAL
        ));
        String paramedicCode = issueCode(admin, ems, UserRole.PARAMEDIC);
        String hospitalCode = issueCode(admin, hospital, UserRole.HOSPITAL_STAFF);

        List<AttemptResult> results = runTogether(
                () -> signupParamedic(paramedicCode, "crossrolelogin"),
                () -> signupHospital(hospitalCode, "crossrolelogin", hospital.getName())
        );

        assertThat(results).allMatch(AttemptResult::success);
        assertThat(userAccountRepository.findByLoginIdAndRole("crossrolelogin", UserRole.PARAMEDIC))
                .isPresent();
        assertThat(userAccountRepository.findByLoginIdAndRole("crossrolelogin", UserRole.HOSPITAL_STAFF))
                .isPresent();
    }

    private AttemptResult signupParamedic(String code, String loginId) {
        try {
            accountSignupService.signupParamedic(new ParamedicSignupRequest(
                    code,
                    "동시가입 대원",
                    loginId,
                    "safe-password",
                    "010-1234-5678",
                    true,
                    "COLLECTION_USE_DEV_1.0",
                    true,
                    "HOSPITAL_PROVISION_DEV_1.0"
            ));
            return AttemptResult.succeeded();
        } catch (CustomException ex) {
            return AttemptResult.failed(ex.getErrorCode().getCode());
        }
    }

    private AttemptResult signupHospital(String code, String loginId, String organizationName) {
        try {
            accountSignupService.signupHospital(new HospitalSignupRequest(
                    code,
                    organizationName,
                    loginId,
                    "safe-password",
                    "서울특별시 성북구",
                    new BigDecimal("37.5821000"),
                    new BigDecimal("127.0105000"),
                    "02-1234-5678",
                    true,
                    "CONTACT_SHARING_DEV_1.0"
            ));
            return AttemptResult.succeeded();
        } catch (CustomException ex) {
            return AttemptResult.failed(ex.getErrorCode().getCode());
        }
    }

    private String issueHospitalCode(UserAccount admin, Organization hospital) {
        return issueCode(admin, hospital, UserRole.HOSPITAL_STAFF);
    }

    private String issueCode(
            UserAccount admin,
            Organization organization,
            UserRole role
    ) {
        return invitationService.issue(
                        admin.getPublicId(),
                        new IssueInvitationRequest(
                                organization.getPublicId(),
                                role,
                                InvitationExpiryOption.THREE_DAYS,
                                null
                        )
                )
                .code();
    }

    private List<AttemptResult> runTogether(
            Callable<AttemptResult> first,
            Callable<AttemptResult> second
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<AttemptResult> firstFuture = executor.submit(waitThenRun(ready, start, first));
            Future<AttemptResult> secondFuture = executor.submit(waitThenRun(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstFuture.get(), secondFuture.get());
        }
    }

    private Callable<AttemptResult> waitThenRun(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<AttemptResult> delegate
    ) {
        return () -> {
            ready.countDown();
            start.await();
            return delegate.call();
        };
    }

    private record AttemptResult(boolean success, String errorCode) {

        static AttemptResult succeeded() {
            return new AttemptResult(true, null);
        }

        static AttemptResult failed(String errorCode) {
            return new AttemptResult(false, errorCode);
        }
    }
}
