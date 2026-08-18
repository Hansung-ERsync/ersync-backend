package com.hansungteam.ersync.profile;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.UpdateHospitalProfileRequest;
import com.hansungteam.ersync.hospital.application.HospitalProfileCommandService;
import com.hansungteam.ersync.hospital.application.HospitalReceivingService;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.api.UpdateParamedicProfileRequest;
import com.hansungteam.ersync.paramedic.application.ParamedicProfileCommandService;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProfileUpdateConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private HospitalProfileCommandService hospitalProfileCommandService;
    @Autowired private HospitalReceivingService hospitalReceivingService;
    @Autowired private ParamedicProfileCommandService paramedicProfileCommandService;

    @Test
    void concurrentHospitalProfileUpdatesNeverMixLocationBundles() throws Exception {
        UserAccount account = createHospital("concurrentprofilehospital");
        AuthenticatedAccount authenticated = authenticated(account, UserRole.HOSPITAL_STAFF);
        UpdateHospitalProfileRequest first = hospitalRequest(
                "서울특별시 종로구 첫 주소",
                "첫 응급실",
                "37.5700000",
                "126.9800000",
                "02-1111-1111"
        );
        UpdateHospitalProfileRequest second = hospitalRequest(
                "서울특별시 강남구 둘째 주소",
                "둘째 응급실",
                "37.5000000",
                "127.0300000",
                "02-2222-2222"
        );
        long auditCountBefore = auditEventRepository.countByAction(AuditAction.HOSPITAL_PROFILE_UPDATED);

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> hospitalProfileCommandService.update(authenticated, first)),
                () -> capture(() -> hospitalProfileCommandService.update(authenticated, second))
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        HospitalProfile saved = hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .orElseThrow();
        assertThat(matches(saved, first) || matches(saved, second)).isTrue();
        assertThat(auditEventRepository.countByAction(AuditAction.HOSPITAL_PROFILE_UPDATED))
                .isEqualTo(auditCountBefore + 2);
    }

    @Test
    void hospitalProfileAndReceivingStatusUpdatesPreserveBothResults() throws Exception {
        UserAccount account = createHospital("profileandstatushospital");
        AuthenticatedAccount authenticated = authenticated(account, UserRole.HOSPITAL_STAFF);
        UpdateHospitalProfileRequest update = hospitalRequest(
                "서울특별시 마포구 새 주소",
                null,
                "37.5500000",
                "126.9100000",
                "02-3333-3333"
        );

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> hospitalProfileCommandService.update(authenticated, update)),
                () -> capture(() -> hospitalReceivingService.change(authenticated, ReceivingStatus.ON))
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        HospitalProfile saved = hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .orElseThrow();
        assertThat(matches(saved, update)).isTrue();
        assertThat(saved.getReceivingStatus()).isEqualTo(ReceivingStatus.ON);
    }

    @Test
    void concurrentParamedicProfileUpdatesNeverMixNameAndContact() throws Exception {
        UserAccount account = createParamedic("concurrentprofilemedic");
        AuthenticatedAccount authenticated = authenticated(account, UserRole.PARAMEDIC);
        UpdateParamedicProfileRequest first = new UpdateParamedicProfileRequest(
                "첫 번째 대원",
                "010-1111-1111"
        );
        UpdateParamedicProfileRequest second = new UpdateParamedicProfileRequest(
                "두 번째 대원",
                "010-2222-2222"
        );
        long auditCountBefore = auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED);

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> paramedicProfileCommandService.update(authenticated, first)),
                () -> capture(() -> paramedicProfileCommandService.update(authenticated, second))
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        ParamedicProfile saved = paramedicProfileRepository.findByAccountPublicId(account.getPublicId())
                .orElseThrow();
        boolean firstComplete = saved.getDisplayName().equals(first.displayName())
                && saved.getContact().equals(first.callbackContact());
        boolean secondComplete = saved.getDisplayName().equals(second.displayName())
                && saved.getContact().equals(second.callbackContact());
        assertThat(firstComplete || secondComplete).isTrue();
        assertThat(auditEventRepository.countByAction(AuditAction.PARAMEDIC_PROFILE_UPDATED))
                .isEqualTo(auditCountBefore + 2);
    }

    private UserAccount createHospital(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        hospitalProfileRepository.save(HospitalProfile.create(
                organization,
                account,
                "서울특별시 성북구",
                "본관 응급실",
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000"),
                "02-1234-5678"
        ));
        return account;
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                account,
                organization,
                "기존 대원",
                "010-0000-0001"
        ));
        Instant consentedAt = Instant.parse("2026-08-04T09:00:00Z");
        consentRepository.save(ContactSharingConsent.record(
                account,
                ConsentType.CONTACT_COLLECTION_USE,
                "COLLECTION_USE_DEV_1.0",
                consentedAt
        ));
        consentRepository.save(ContactSharingConsent.record(
                account,
                ConsentType.HOSPITAL_PROVISION,
                "HOSPITAL_PROVISION_DEV_1.0",
                consentedAt
        ));
        return account;
    }

    private AuthenticatedAccount authenticated(UserAccount account, UserRole role) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                role
        );
    }

    private UpdateHospitalProfileRequest hospitalRequest(
            String address,
            String detailAddress,
            String latitude,
            String longitude,
            String contact
    ) {
        return new UpdateHospitalProfileRequest(
                address,
                detailAddress,
                new BigDecimal(latitude),
                new BigDecimal(longitude),
                contact
        );
    }

    private boolean matches(HospitalProfile profile, UpdateHospitalProfileRequest request) {
        return profile.getAddress().equals(request.address())
                && java.util.Objects.equals(profile.getDetailAddress(), request.detailAddress())
                && profile.getLatitude().compareTo(request.latitude()) == 0
                && profile.getLongitude().compareTo(request.longitude()) == 0
                && profile.getContact().equals(request.contact());
    }

    private Outcome capture(ThrowingSupplier supplier) {
        try {
            return Outcome.success(supplier.get());
        } catch (Exception exception) {
            return Outcome.failure(exception);
        }
    }

    private List<Outcome> runTogether(Callable<Outcome> first, Callable<Outcome> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Outcome> firstFuture = executor.submit(awaitThenCall(ready, start, first));
            Future<Outcome> secondFuture = executor.submit(awaitThenCall(ready, start, second));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(
                    firstFuture.get(10, TimeUnit.SECONDS),
                    secondFuture.get(10, TimeUnit.SECONDS)
            );
        }
    }

    private Callable<Outcome> awaitThenCall(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<Outcome> delegate
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent test did not start");
            }
            return delegate.call();
        };
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        Object get() throws Exception;
    }

    private record Outcome(boolean successful, Object value, Exception exception) {

        static Outcome success(Object value) {
            return new Outcome(true, value, null);
        }

        static Outcome failure(Exception exception) {
            return new Outcome(false, null, exception);
        }
    }
}
