package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.api.CancelTransportRequestRequest;
import com.hansungteam.ersync.transport.api.TransportRequestDetailResponse;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.application.TransportClinicalUpdateService;
import com.hansungteam.ersync.transport.application.TransportLifecycleService;
import com.hansungteam.ersync.transport.application.TransportRequestDetailQueryService;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransportRequestDetailConcurrencyIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private ParamedicProfileRepository profileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private TransportClinicalUpdateService clinicalUpdateService;
    @Autowired private TransportLifecycleService lifecycleService;
    @Autowired private TransportRequestDetailQueryService detailQueryService;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private RealtimeOutboxEventRepository outboxRepository;

    @Test
    void clinicalUpdateRaceReturnsOneCompleteSnapshotAndConvergesToTheNewSnapshot() throws Exception {
        UserAccount paramedic = createParamedic("detailupdateracemedic");
        AuthenticatedAccount principal = principal(paramedic);
        String requestId = requestService.create(
                principal, "detail-update-race-create", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        Instant initialMeasuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt();
        Instant newMeasuredAt = initialMeasuredAt.plusSeconds(60);

        DetailOutcome raced = runTogether(
                () -> captureDetail(principal, requestId),
                () -> clinicalUpdateService.addVitalSigns(
                        principal,
                        requestId,
                        "detail-update-race-vitals",
                        vitalRequest(newMeasuredAt, "111")
                )
        );

        assertThat(raced.errorCode()).isNull();
        assertCompleteVitalSnapshot(raced.response(), initialMeasuredAt, newMeasuredAt);

        TransportRequestDetailResponse converged = detailQueryService.detail(principal, requestId);
        assertThat(converged.latestSnapshot().vitalSigns().measuredAt()).isEqualTo(newMeasuredAt);
        assertThat(pulse(converged)).isEqualByComparingTo("111");
    }

    @Test
    void cancellationRaceReturnsActiveDetailOrNotFoundAndEveryLaterReadIsNotFound() throws Exception {
        UserAccount paramedic = createParamedic("detailcancelracemedic");
        AuthenticatedAccount principal = principal(paramedic);
        String requestId = requestService.create(
                principal, "detail-cancel-race-create", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();

        DetailOutcome raced = runTogether(
                () -> captureDetail(principal, requestId),
                () -> lifecycleService.cancel(
                        principal,
                        requestId,
                        "detail-cancel-race-command",
                        new CancelTransportRequestRequest(TransportCancellationReason.SCENE_RESOLVED, null)
                )
        );

        if (raced.response() != null) {
            assertThat(raced.response().status()).isEqualTo(TransportRequestStatus.SEARCHING);
            assertThat(raced.response().patient()).isNotNull();
            assertThat(raced.response().incident()).isNotNull();
            assertThat(raced.response().latestSnapshot()).isNotNull();
        } else {
            assertThat(raced.errorCode()).isEqualTo("TRANSPORT_001");
        }

        DetailOutcome converged = captureDetail(principal, requestId);
        assertThat(converged.response()).isNull();
        assertThat(converged.errorCode()).isEqualTo("TRANSPORT_001");
    }

    @Test
    void detailUpdateAndCancellationTripleRaceNeverReturnsPartialPatientData() throws Exception {
        UserAccount paramedic = createParamedic("detailtripleracemedic");
        AuthenticatedAccount principal = principal(paramedic);
        String requestId = requestService.create(
                principal, "detail-triple-race-create", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        Instant initialMeasuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt();
        Instant newMeasuredAt = initialMeasuredAt.plusSeconds(60);

        TripleRaceOutcome raced = runThreeTogether(
                () -> captureDetail(principal, requestId),
                () -> captureCommand(() -> clinicalUpdateService.addVitalSigns(
                        principal,
                        requestId,
                        "detail-triple-race-vitals",
                        vitalRequest(newMeasuredAt, "111")
                )),
                () -> captureCommand(() -> lifecycleService.cancel(
                        principal,
                        requestId,
                        "detail-triple-race-cancel",
                        new CancelTransportRequestRequest(TransportCancellationReason.SCENE_RESOLVED, null)
                ))
        );

        assertThat(raced.cancellation().successful()).isTrue();
        assertThat(raced.update().successful() || raced.update().errorCode().equals("TRANSPORT_004")).isTrue();
        if (raced.detail().response() != null) {
            assertThat(raced.detail().response().status()).isEqualTo(TransportRequestStatus.SEARCHING);
            assertThat(raced.detail().response().patient()).isNotNull();
            assertThat(raced.detail().response().incident()).isNotNull();
            assertCompleteVitalSnapshot(raced.detail().response(), initialMeasuredAt, newMeasuredAt);
        } else {
            assertThat(raced.detail().errorCode()).isEqualTo("TRANSPORT_001");
        }

        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.CANCELLED);
        DetailOutcome converged = captureDetail(principal, requestId);
        assertThat(converged.response()).isNull();
        assertThat(converged.errorCode()).isEqualTo("TRANSPORT_001");
    }

    @Test
    void ownerAndStrangerConcurrentReadsNeverLeakTheOwnersPatient() throws Exception {
        UserAccount owner = createParamedic("detailownerracemedic");
        UserAccount stranger = createParamedic("detailstrangerracemedic");
        AuthenticatedAccount ownerPrincipal = principal(owner);
        AuthenticatedAccount strangerPrincipal = principal(stranger);
        String requestId = requestService.create(
                ownerPrincipal, "detail-owner-stranger-create", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();

        List<DetailOutcome> outcomes = runQueriesTogether(List.of(
                () -> captureDetail(ownerPrincipal, requestId),
                () -> captureDetail(strangerPrincipal, requestId)
        ));

        assertThat(outcomes.getFirst().errorCode()).isNull();
        assertThat(outcomes.getFirst().response().transportRequestId()).isEqualTo(requestId);
        assertThat(outcomes.getFirst().response().patient()).isNotNull();
        assertThat(outcomes.get(1).response()).isNull();
        assertThat(outcomes.get(1).errorCode()).isEqualTo("TRANSPORT_001");
    }

    @Test
    void manyConcurrentDetailReadsAreIdenticalAndHaveNoSideEffects() throws Exception {
        UserAccount paramedic = createParamedic("detailreadburstmedic");
        AuthenticatedAccount principal = principal(paramedic);
        String requestId = requestService.create(
                principal, "detail-read-burst-create", ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        long auditCount = auditRepository.count();
        long outboxCount = outboxRepository.count();
        Instant requestUpdatedAt = requestRepository.findByPublicId(requestId).orElseThrow().getUpdatedAt();
        List<Callable<DetailOutcome>> queries = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            queries.add(() -> captureDetail(principal, requestId));
        }

        List<DetailOutcome> outcomes = runQueriesTogether(queries);

        assertThat(outcomes).allSatisfy(outcome -> {
            assertThat(outcome.errorCode()).isNull();
            assertThat(outcome.response().transportRequestId()).isEqualTo(requestId);
            assertThat(outcome.response().status()).isEqualTo(TransportRequestStatus.SEARCHING);
            assertThat(outcome.response().latestSnapshot().vitalSigns().measurements()).hasSize(5);
        });
        assertThat(outcomes.stream().map(outcome -> outcome.response().patient()).distinct())
                .hasSize(1);
        assertThat(outcomes.stream().map(outcome -> outcome.response().incident()).distinct())
                .hasSize(1);
        assertThat(outcomes.stream().map(outcome -> outcome.response().latestSnapshot()).distinct())
                .hasSize(1);
        assertThat(auditRepository.count()).isEqualTo(auditCount);
        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getUpdatedAt())
                .isEqualTo(requestUpdatedAt);
    }

    private void assertCompleteVitalSnapshot(
            TransportRequestDetailResponse response,
            Instant initialMeasuredAt,
            Instant newMeasuredAt
    ) {
        assertThat(response).isNotNull();
        assertThat(response.latestSnapshot()).isNotNull();
        assertThat(response.latestSnapshot().vitalSigns().measurements()).hasSize(5);
        Instant returnedAt = response.latestSnapshot().vitalSigns().measuredAt();
        assertThat(returnedAt).isIn(initialMeasuredAt, newMeasuredAt);
        if (returnedAt.equals(initialMeasuredAt)) {
            assertThat(pulse(response)).isEqualByComparingTo("80");
        } else {
            assertThat(pulse(response)).isEqualByComparingTo("111");
        }
    }

    private BigDecimal pulse(TransportRequestDetailResponse response) {
        return response.latestSnapshot().vitalSigns().measurements().stream()
                .filter(measurement -> measurement.type().equals(VitalSignType.PULSE.name()))
                .findFirst()
                .orElseThrow()
                .primaryValue();
    }

    private UpdateVitalSignsRequest vitalRequest(Instant measuredAt, String pulse) {
        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        return new UpdateVitalSignsRequest(
                measuredAt,
                measuredAt.plusSeconds(1),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(),
                        measurement.state(),
                        measurement.type() == VitalSignType.PULSE
                                ? new BigDecimal(pulse)
                                : measurement.primaryValue(),
                        measurement.secondaryValue(),
                        measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );
    }

    private DetailOutcome captureDetail(AuthenticatedAccount principal, String requestId) {
        try {
            return new DetailOutcome(detailQueryService.detail(principal, requestId), null);
        } catch (CustomException exception) {
            return new DetailOutcome(null, exception.getErrorCode().getCode());
        }
    }

    private CommandOutcome captureCommand(Runnable command) {
        try {
            command.run();
            return new CommandOutcome(true, null);
        } catch (CustomException exception) {
            return new CommandOutcome(false, exception.getErrorCode().getCode());
        }
    }

    private DetailOutcome runTogether(Callable<DetailOutcome> query, Runnable command) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<DetailOutcome> queryFuture = executor.submit(awaitThenCall(ready, start, query));
            Future<?> commandFuture = executor.submit(awaitThenRun(ready, start, command));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            DetailOutcome outcome = queryFuture.get(10, TimeUnit.SECONDS);
            commandFuture.get(10, TimeUnit.SECONDS);
            return outcome;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private TripleRaceOutcome runThreeTogether(
            Callable<DetailOutcome> detail,
            Callable<CommandOutcome> update,
            Callable<CommandOutcome> cancellation
    ) throws Exception {
        CountDownLatch ready = new CountDownLatch(3);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(3);
        try {
            Future<DetailOutcome> detailFuture = executor.submit(awaitThenCall(ready, start, detail));
            Future<CommandOutcome> updateFuture = executor.submit(awaitThenCall(ready, start, update));
            Future<CommandOutcome> cancellationFuture = executor.submit(awaitThenCall(ready, start, cancellation));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return new TripleRaceOutcome(
                    detailFuture.get(10, TimeUnit.SECONDS),
                    updateFuture.get(10, TimeUnit.SECONDS),
                    cancellationFuture.get(10, TimeUnit.SECONDS)
            );
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<DetailOutcome> runQueriesTogether(List<Callable<DetailOutcome>> queries) throws Exception {
        CountDownLatch ready = new CountDownLatch(queries.size());
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(queries.size());
        try {
            List<Future<DetailOutcome>> futures = queries.stream()
                    .map(query -> executor.submit(awaitThenCall(ready, start, query)))
                    .toList();
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<DetailOutcome> outcomes = new ArrayList<>();
            for (Future<DetailOutcome> future : futures) {
                outcomes.add(future.get(10, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private <T> Callable<T> awaitThenCall(
            CountDownLatch ready,
            CountDownLatch start,
            Callable<T> command
    ) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent detail query did not start");
            }
            return command.call();
        };
    }

    private Runnable awaitThenRun(CountDownLatch ready, CountDownLatch start, Runnable command) {
        return () -> {
            ready.countDown();
            try {
                if (!start.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent detail command did not start");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Concurrent detail command interrupted", exception);
            }
            command.run();
        };
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = accountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", UserRole.PARAMEDIC
        ));
        profileRepository.save(ParamedicProfile.create(
                account, organization, loginId + " 대원", "010-0000-0001"
        ));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private AuthenticatedAccount principal(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(), account.getOrganization().getPublicId(), UserRole.PARAMEDIC
        );
    }

    private record DetailOutcome(TransportRequestDetailResponse response, String errorCode) {
    }

    private record CommandOutcome(boolean successful, String errorCode) {
    }

    private record TripleRaceOutcome(
            DetailOutcome detail,
            CommandOutcome update,
            CommandOutcome cancellation
    ) {
    }
}
