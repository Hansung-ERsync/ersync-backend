package com.hansungteam.ersync.global;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.api.ParamedicSignupRequest;
import com.hansungteam.ersync.account.application.AccountSignupService;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.HospitalReceivingStatusResponse;
import com.hansungteam.ersync.hospital.application.HospitalReceivingService;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.destination.infrastructure.TransportDestinationCommandRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "ersync.auth.jwt-secret-base64=dGVzdC1qd3Qtc2VjcmV0LWtleS0zMi1ieXRlcy1mb3ItdGVzdHM=",
        "ersync.invitation.expiry-scheduler-enabled=false",
        "ersync.hospital-search.scheduler-enabled=false",
        "ersync.maps.naver.eta-scheduler-enabled=false",
        "ersync.realtime.scheduler-enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
@AutoConfigureMockMvc
class MySqlDatabaseIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("ersync")
            .withUsername("ersync_local")
            .withPassword("ersync_local_password");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private AccountSignupService accountSignupService;
    @Autowired private SecretDigester secretDigester;
    @Autowired private InvitationService invitationService;
    @Autowired private InvitationCodeRepository invitationCodeRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalReceivingService hospitalReceivingService;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private HospitalDispatchAttemptRepository dispatchAttemptRepository;
    @Autowired private HospitalOfferRepository hospitalOfferRepository;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private HospitalOfferService hospitalOfferService;
    @Autowired private TransportDestinationService transportDestinationService;
    @Autowired private TransportDestinationCommandRepository destinationCommandRepository;

    @DynamicPropertySource
    static void registerMySqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlDatabaseIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void flywayAndJpaValidateRunOnMySql84() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        Integer featureTableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'organizations',
                      'user_accounts',
                      'invitation_codes',
                      'hospital_profiles',
                      'refresh_tokens',
                      'audit_events',
                      'paramedic_profiles',
                      'contact_sharing_consents',
                      'transport_requests',
                      'patient_demographics',
                      'incident_assessments',
                      'incident_injury_sites',
                      'incident_secondary_symptoms',
                      'pre_ktas_assessments',
                      'consciousness_assessments',
                      'vital_sign_sets',
                      'vital_sign_measurements',
                      'treatment_events',
                      'current_patient_snapshots',
                      'current_patient_snapshot_treatments',
                      'hospital_dispatch_attempts',
                      'hospital_search_rounds',
                      'hospital_offers',
                      'hospital_offer_events',
                      'realtime_outbox_events',
                      'transport_destination_commands',
                      'transport_update_commands',
                      'transport_current_locations',
                      'supplemental_assessment_records',
                      'general_supplemental_assessments'
                  )
                """, Integer.class);

        assertThat(version).startsWith("8.4");
        assertThat(featureTableCount).isEqualTo(30);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND (
                    (table_name = 'transport_requests' AND column_name = 'current_destination_offer_id')
                    OR (table_name = 'hospital_offers' AND column_name = 'withdrawal_reason')
                    OR (table_name = 'hospital_dispatch_attempts' AND column_name = 'trigger_type')
                    OR (table_name = 'hospital_offers' AND column_name = 'route_estimate_generation')
                    OR (table_name = 'hospital_offers' AND column_name = 'last_success_route_distance_m')
                    OR (table_name = 'hospital_offers' AND column_name = 'last_success_eta_seconds')
                    OR (table_name = 'hospital_offers' AND column_name = 'last_success_eta_calculated_at')
                    OR (table_name = 'paramedic_profiles' AND column_name = 'display_name')
                    OR (table_name = 'contact_sharing_consents' AND column_name = 'consent_type')
                    OR (table_name = 'current_patient_snapshots'
                        AND column_name = 'latest_supplemental_assessment_id')
                    OR (table_name = 'hospital_offers'
                        AND column_name = 'clinical_visibility_cutoff_at')
                    OR (table_name = 'hospital_offers'
                        AND column_name = 'frozen_last_clinical_update_at')
                  )
                """, Integer.class)).isEqualTo(12);
    }

    @Test
    void readinessIsUpWithMigratedMySql84() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Transactional
    void eightCharacterInvitationPersistsAndIsConsumedOnMySql84() {
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "mysqlinviteadmin",
                "encoded-password"
        ));
        Organization emsUnit = organizationRepository.save(Organization.create(
                "MySQL 8자리 코드 구급대",
                OrganizationType.EMS_UNIT
        ));

        var issued = invitationService.issue(
                admin.getPublicId(),
                new IssueInvitationRequest(
                        emsUnit.getPublicId(),
                        UserRole.PARAMEDIC,
                        InvitationExpiryOption.THREE_DAYS,
                        null
                )
        );
        assertThat(issued.code()).matches("[A-Za-z0-9_-]{8}");
        assertThat(invitationCodeRepository.existsByCodeDigest(secretDigester.digest(issued.code()))).isTrue();

        accountSignupService.signupParamedic(new ParamedicSignupRequest(
                issued.code(),
                "MySQL 코드 대원",
                "mysqlinvitemedic",
                "safe-password",
                "010-0000-0014",
                true,
                "COLLECTION_USE_DEV_1.0",
                true,
                "HOSPITAL_PROVISION_DEV_1.0"
        ));

        assertThat(invitationCodeRepository.findByPublicId(issued.invitation().invitationCodeId()))
                .get()
                .extracting(invitation -> invitation.getStatus())
                .isEqualTo(InvitationStatus.USED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT OCTET_LENGTH(code_digest) FROM invitation_codes WHERE public_id = ?",
                Integer.class,
                issued.invitation().invitationCodeId()
        )).isEqualTo(32);
    }

    @Test
    void completeTransportRequestPersistsUnderMySqlConstraints() {
        Organization organization = organizationRepository.save(Organization.create(
                "MySQL 통합 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "mysqlmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(account, organization, "010-0000-0084"));
        consentRepository.save(ContactSharingConsent.record(
                account,
                "CONTACT_SHARING_DEV_1.0",
                Instant.parse("2026-08-03T09:00:00Z")
        ));

        var base = ValidTransportRequestFixtures.request();
        var request = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                base.assessmentProtocolVersion(),
                base.origin(),
                base.patient(),
                base.incident(),
                base.preKtas(),
                base.consciousness(),
                base.vitalSigns(),
                base.treatments(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.SupplementalAssessmentInput(
                        Instant.parse("2026-08-03T10:00:00Z"),
                        Instant.parse("2026-08-03T10:01:00Z"),
                        85,
                        com.hansungteam.ersync.transport.domain.PupilResponse.NORMAL,
                        com.hansungteam.ersync.transport.domain.PupilResponse.SLUGGISH,
                        "고혈압",
                        null,
                        null,
                        false
                )
        );
        var result = transportRequestService.create(
                new AuthenticatedAccount(account.getPublicId(), organization.getPublicId(), UserRole.PARAMEDIC),
                "mysql-request-key-84",
                request
        );

        assertThat(result.created()).isTrue();
        assertThat(transportRequestRepository.findByPublicId(result.response().transportRequestId())).isPresent();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vital_sign_measurements", Integer.class))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM current_patient_snapshots", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM current_patient_snapshot_treatments",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplemental_assessment_records",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM general_supplemental_assessments WHERE isolation_concern = FALSE",
                Integer.class
        )).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE general_supplemental_assessments SET glucose_mg_dl = 1001"
        )).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM current_patient_snapshots snapshot
                WHERE snapshot.patient_demographics_id IS NOT NULL
                  AND snapshot.incident_assessment_id IS NOT NULL
                  AND snapshot.assessment_protocol_version = 'ERSYNC_MVP_1.0'
                  AND snapshot.latest_supplemental_assessment_id IS NOT NULL
                  AND snapshot.last_clinical_update_at = (
                      SELECT request.server_received_at
                      FROM transport_requests request
                      WHERE request.id = snapshot.transport_request_id
                  )
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @Transactional
    void latestEffectiveDestinationProjectionRunsOnMySql84() {
        Organization paramedicOrganization = organizationRepository.save(Organization.create(
                "MySQL 목적지 조회 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount paramedic = userAccountRepository.save(UserAccount.createMember(
                paramedicOrganization,
                "mysqlprojectionmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                paramedic,
                paramedicOrganization,
                "010-0000-0085"
        ));
        consentRepository.save(ContactSharingConsent.record(
                paramedic,
                "CONTACT_SHARING_DEV_1.0",
                Instant.parse("2026-08-06T09:00:00Z")
        ));

        UserAccount hospitalOne = createReceivingHospital(
                "mysqlprojectionhospital1",
                "MySQL 목적지 조회 1병원",
                "37.6021000"
        );
        UserAccount hospitalTwo = createReceivingHospital(
                "mysqlprojectionhospital2",
                "MySQL 목적지 조회 2병원",
                "37.6121000"
        );
        Long hospitalOneProfileId = hospitalProfileRepository.findByAccountPublicId(hospitalOne.getPublicId())
                .orElseThrow()
                .getId();
        Long hospitalTwoProfileId = hospitalProfileRepository.findByAccountPublicId(hospitalTwo.getPublicId())
                .orElseThrow()
                .getId();
        AuthenticatedAccount paramedicPrincipal = new AuthenticatedAccount(
                paramedic.getPublicId(),
                paramedicOrganization.getPublicId(),
                UserRole.PARAMEDIC
        );
        String requestId = transportRequestService.create(
                paramedicPrincipal,
                "mysql-projection-request",
                ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
        var attempt = dispatchAttemptRepository
                .findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        var offers = hospitalOfferRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getId().equals(hospitalOneProfileId))
                .findFirst()
                .orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getId().equals(hospitalTwoProfileId))
                .findFirst()
                .orElseThrow();

        hospitalOfferService.accept(hospitalPrincipal(hospitalOne), offerOne.getPublicId(), "mysql-accept-1");
        hospitalOfferService.accept(hospitalPrincipal(hospitalTwo), offerTwo.getPublicId(), "mysql-accept-2");
        transportDestinationService.select(
                paramedicPrincipal,
                requestId,
                "mysql-destination-1",
                offerOne.getPublicId()
        );
        var changed = transportDestinationService.select(
                paramedicPrincipal,
                requestId,
                "mysql-destination-2",
                offerTwo.getPublicId()
        );
        transportDestinationService.select(
                paramedicPrincipal,
                requestId,
                "mysql-destination-3",
                offerTwo.getPublicId()
        );

        Long transportRequestId = transportRequestRepository.findByPublicId(requestId).orElseThrow().getId();
        assertThat(destinationCommandRepository.findLatestEffectiveDestinations(Set.of(transportRequestId)))
                .singleElement()
                .satisfies(destination -> {
                    assertThat(destination.getTransportRequestId()).isEqualTo(transportRequestId);
                    assertThat(destination.getDestinationOfferId()).isEqualTo(offerTwo.getId());
                    assertThat(destination.getOccurredAt()).isNotNull();
                    assertThat(destination.getOccurredAt())
                            .isCloseTo(changed.changedAt(), within(1, ChronoUnit.MICROS));
                });
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = 'transport_destination_commands'
                  AND index_name = 'idx_destination_commands_request_occurred'
                  AND column_name IN ('transport_request_id', 'occurred_at')
                """, Integer.class)).isEqualTo(2);
    }

    @Test
    void concurrentHospitalReceivingChangesAreSerializedOnMySql84() throws Exception {
        Organization organization = organizationRepository.save(Organization.create(
                "MySQL 동시 수신 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "mysqlhospital",
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
        AuthenticatedAccount authenticated = new AuthenticatedAccount(
                account.getPublicId(),
                organization.getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<HospitalReceivingStatusResponse> onFuture = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return hospitalReceivingService.change(authenticated, ReceivingStatus.ON);
            });
            Future<HospitalReceivingStatusResponse> offFuture = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return hospitalReceivingService.change(authenticated, ReceivingStatus.OFF);
            });

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(
                    onFuture.get(10, TimeUnit.SECONDS).status(),
                    offFuture.get(10, TimeUnit.SECONDS).status()
            )).containsExactlyInAnyOrder(ReceivingStatus.ON, ReceivingStatus.OFF);
        }

        assertThat(hospitalProfileRepository.findByAccountPublicId(account.getPublicId()))
                .get()
                .extracting(HospitalProfile::getReceivingStatus)
                .isIn(ReceivingStatus.ON, ReceivingStatus.OFF);
    }

    private UserAccount createReceivingHospital(String loginId, String organizationName, String latitude) {
        Organization organization = organizationRepository.save(Organization.create(
                organizationName,
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                loginId,
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = HospitalProfile.create(
                organization,
                account,
                "서울특별시 테스트 주소",
                new BigDecimal(latitude),
                new BigDecimal("127.0105000"),
                "02-0000-0085"
        );
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private AuthenticatedAccount hospitalPrincipal(UserAccount account) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
    }

    private static String jdbcUrl() {
        return MYSQL.getJdbcUrl()
                + "?connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true";
    }
}
