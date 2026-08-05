package com.hansungteam.ersync.hospital;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.HospitalProfileResponse;
import com.hansungteam.ersync.hospital.api.HospitalReceivingStatusResponse;
import com.hansungteam.ersync.hospital.application.HospitalProfileQueryService;
import com.hansungteam.ersync.hospital.application.HospitalReceivingService;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
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
class HospitalProfileConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private HospitalReceivingService hospitalReceivingService;
    @Autowired private HospitalProfileQueryService hospitalProfileQueryService;

    @Test
    void concurrentSameReceivingStatusChangesDoNotExposePersistenceFailure() throws Exception {
        UserAccount account = createHospital("concurrentstatushospital");
        AuthenticatedAccount authenticated = authenticated(account);
        long auditCountBefore = auditEventRepository.count();

        for (int index = 0; index < 10; index++) {
            ReceivingStatus target = index % 2 == 0 ? ReceivingStatus.ON : ReceivingStatus.OFF;
            List<Outcome> outcomes = runTogether(
                    () -> capture(() -> hospitalReceivingService.change(authenticated, target)),
                    () -> capture(() -> hospitalReceivingService.change(authenticated, target))
            );

            assertThat(outcomes).allMatch(Outcome::successful);
            assertThat(hospitalProfileQueryService.getMine(authenticated).receivingStatus())
                    .isEqualTo(target);
        }

        assertThat(auditEventRepository.count()).isEqualTo(auditCountBefore + 20);
    }

    @Test
    void concurrentOppositeReceivingStatusChangesAreSerialized() throws Exception {
        UserAccount account = createHospital("oppositestatushospital");
        AuthenticatedAccount authenticated = authenticated(account);
        long auditCountBefore = auditEventRepository.count();

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> hospitalReceivingService.change(
                        authenticated,
                        ReceivingStatus.ON
                )),
                () -> capture(() -> hospitalReceivingService.change(
                        authenticated,
                        ReceivingStatus.OFF
                ))
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        assertThat(outcomes.stream()
                .map(Outcome::value)
                .filter(HospitalReceivingStatusResponse.class::isInstance)
                .map(HospitalReceivingStatusResponse.class::cast)
                .map(HospitalReceivingStatusResponse::status)
                .toList())
                .containsExactlyInAnyOrder(ReceivingStatus.ON, ReceivingStatus.OFF);
        assertThat(hospitalProfileQueryService.getMine(authenticated).receivingStatus())
                .isIn(ReceivingStatus.ON, ReceivingStatus.OFF);
        assertThat(auditEventRepository.count()).isEqualTo(auditCountBefore + 2);
    }

    @Test
    void profileReadAndReceivingChangeCanOverlapWithoutPartialState() throws Exception {
        UserAccount account = createHospital("concurrentreadhospital");
        AuthenticatedAccount authenticated = authenticated(account);

        List<Outcome> outcomes = runTogether(
                () -> capture(() -> hospitalProfileQueryService.getMine(authenticated)),
                () -> capture(() -> hospitalReceivingService.change(
                        authenticated,
                        ReceivingStatus.ON
                ))
        );

        assertThat(outcomes).allMatch(Outcome::successful);
        assertThat(outcomes.stream()
                .map(Outcome::value)
                .filter(HospitalProfileResponse.class::isInstance)
                .map(HospitalProfileResponse.class::cast)
                .map(HospitalProfileResponse::receivingStatus)
                .toList())
                .allMatch(status -> status == ReceivingStatus.OFF || status == ReceivingStatus.ON);
        assertThat(hospitalProfileQueryService.getMine(authenticated).receivingStatus())
                .isEqualTo(ReceivingStatus.ON);
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
                new BigDecimal("37.5821000"),
                new BigDecimal("127.0105000"),
                "02-1234-5678"
        ));
        return account;
    }

    private AuthenticatedAccount authenticated(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
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
