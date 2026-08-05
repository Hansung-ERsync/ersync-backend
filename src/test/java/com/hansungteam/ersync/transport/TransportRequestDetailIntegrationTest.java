package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
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
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.application.TransportRequestDetailQueryService;
import com.hansungteam.ersync.transport.domain.TransportCancellationReason;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransportRequestDetailIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository accountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private CurrentPatientSnapshotRepository snapshotRepository;
    @Autowired private AuditEventRepository auditRepository;
    @Autowired private RealtimeOutboxEventRepository outboxRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TransportRequestDetailQueryService detailQueryService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void ownerRestoresInitialPatientIncidentAndClinicalSnapshotWithoutSensitiveFields() throws Exception {
        UserAccount owner = createParamedic("detailowner");
        String requestId = createRequest(owner, "detail-request-key-001");
        String ownerAuthorization = bearer(owner);
        long auditCount = auditRepository.count();
        long outboxCount = outboxRepository.count();
        entityManager.flush();
        entityManager.clear();
        Instant requestUpdatedAt = requestRepository.findByPublicId(requestId).orElseThrow().getUpdatedAt();

        String detailBody = mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, ownerAuthorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestId").value(requestId))
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.assessmentProtocolVersion").value("ERSYNC_MVP_1.0"))
                .andExpect(jsonPath("$.patient.ageStatus").value("ESTIMATED"))
                .andExpect(jsonPath("$.patient.ageYears").value(45))
                .andExpect(jsonPath("$.patient.sex").value("UNKNOWN"))
                .andExpect(jsonPath("$.incident.occurrenceType").value("DISEASE"))
                .andExpect(jsonPath("$.incident.occurrenceDetail").doesNotExist())
                .andExpect(jsonPath("$.incident.injuryMechanism").doesNotExist())
                .andExpect(jsonPath("$.incident.injurySites.length()").value(0))
                .andExpect(jsonPath("$.incident.primarySymptom").value("CHEST_PAIN"))
                .andExpect(jsonPath("$.incident.secondarySymptoms[0]").value("DYSPNEA"))
                .andExpect(jsonPath("$.incident.onsetTimeStatus").value("ESTIMATED"))
                .andExpect(jsonPath("$.latestSnapshot.preKtas.level").value(2))
                .andExpect(jsonPath("$.latestSnapshot.consciousness.avpu").value("A"))
                .andExpect(jsonPath("$.latestSnapshot.vitalSigns.measurements.length()").value(5))
                .andExpect(jsonPath("$.latestSnapshot.treatments[0].type").value("NONE"))
                .andExpect(jsonPath("$.latestSnapshot.lastClinicalUpdateAt").exists())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.serverNow").exists())
                .andExpect(jsonPath("$.callbackContact").doesNotExist())
                .andExpect(jsonPath("$.origin").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String timelineBody = mockMvc.perform(get(
                                "/api/v1/transport-requests/{requestId}/clinical-timeline", requestId
                        )
                        .header(HttpHeaders.AUTHORIZATION, ownerAuthorization))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(detailBody).get("latestSnapshot"))
                .isEqualTo(objectMapper.readTree(timelineBody).get("latestSnapshot"));
        assertThat(auditRepository.count()).isEqualTo(auditCount);
        assertThat(outboxRepository.count()).isEqualTo(outboxCount);
        entityManager.flush();
        entityManager.clear();
        assertThat(requestRepository.findByPublicId(requestId).orElseThrow().getUpdatedAt())
                .isEqualTo(requestUpdatedAt);
    }

    @Test
    void detailUsesLatestSnapshotWhileLateClinicalRecordRemainsOnlyInTimeline() throws Exception {
        UserAccount owner = createParamedic("latestdetailowner");
        String requestId = createRequest(owner, "detail-request-key-002");
        Instant newestAt = Instant.parse("2026-08-03T10:10:00Z");
        Instant lateAt = Instant.parse("2026-08-03T10:05:00Z");

        addVitalSigns(owner, requestId, "detail-vital-newest", newestAt, "99");
        addVitalSigns(owner, requestId, "detail-vital-late", lateAt, "70");

        String detailBody = mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestSnapshot.vitalSigns.measuredAt").value(newestAt.toString()))
                .andExpect(jsonPath(
                        "$.latestSnapshot.vitalSigns.measurements[?(@.type == 'PULSE')].primaryValue"
                ).value(org.hamcrest.Matchers.contains(99)))
                .andReturn().getResponse().getContentAsString();

        String timelineBody = mockMvc.perform(get(
                                "/api/v1/transport-requests/{requestId}/clinical-timeline", requestId
                )
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(timelineBody).contains("\"primaryValue\":70");
        assertThat(objectMapper.readTree(detailBody).get("latestSnapshot"))
                .isEqualTo(objectMapper.readTree(timelineBody).get("latestSnapshot"));
    }

    @Test
    void authenticationRoleOrganizationAndOwnershipAreEnforced() throws Exception {
        UserAccount owner = createParamedic("accessdetailowner");
        String requestId = createRequest(owner, "detail-request-key-003");
        UserAccount sameOrganizationStranger = accountRepository.save(UserAccount.createMember(
                owner.getOrganization(), "accessdetailother", "encoded-password", UserRole.PARAMEDIC
        ));
        UserAccount otherOrganizationStranger = createParamedic("accessdetailforeign");
        UserAccount hospital = createMember("accessdetailhospital", OrganizationType.HOSPITAL, UserRole.HOSPITAL_STAFF);
        UserAccount admin = accountRepository.save(UserAccount.createSuperAdmin(
                "accessdetailadmin", "encoded-password"
        ));

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_001"));
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
        assertNotFound(requestId, sameOrganizationStranger);
        assertNotFound(requestId, otherOrganizationStranger);
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        String inactiveBearer = bearer(owner);
        owner.deactivate();
        accountRepository.flush();
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, inactiveBearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    void everyActiveStatusIsReadableButInactiveOrganizationAndStaleOrganizationClaimAreRejected()
            throws Exception {
        for (TransportRequestStatus activeStatus : java.util.List.of(
                TransportRequestStatus.SEARCHING,
                TransportRequestStatus.CANDIDATES_EXHAUSTED,
                TransportRequestStatus.ACCEPTED_AVAILABLE,
                TransportRequestStatus.EN_ROUTE,
                TransportRequestStatus.HANDOFF_REQUESTED
        )) {
            String suffix = String.valueOf(activeStatus.ordinal());
            UserAccount owner = createParamedic("activestatusdetail" + suffix);
            String token = bearer(owner);
            String requestId = createRequest(owner, "detail-active-status-key-" + suffix);
            jdbcTemplate.update(
                    "update transport_requests set status = ? where public_id = ?",
                    activeStatus.name(),
                    requestId
            );
            entityManager.clear();

            mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                            .header(HttpHeaders.AUTHORIZATION, token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(activeStatus.name()));
        }

        UserAccount inactiveOrganizationOwner = createParamedic("inactiveorgdetail");
        String inactiveOrganizationToken = bearer(inactiveOrganizationOwner);
        String inactiveOrganizationRequestId = createRequest(
                inactiveOrganizationOwner, "detail-inactive-organization-key"
        );
        inactiveOrganizationOwner.getOrganization().deactivate();
        organizationRepository.flush();
        mockMvc.perform(get(
                                "/api/v1/transport-requests/{requestId}",
                                inactiveOrganizationRequestId
                        )
                        .header(HttpHeaders.AUTHORIZATION, inactiveOrganizationToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON_004"));

        UserAccount staleClaimOwner = createParamedic("staleorgdetail");
        String staleClaimRequestId = createRequest(staleClaimOwner, "detail-stale-organization-key");
        assertThatThrownBy(() -> detailQueryService.detail(
                        new AuthenticatedAccount(
                                staleClaimOwner.getPublicId(),
                                "00000000-0000-0000-0000-000000000000",
                                UserRole.PARAMEDIC
                        ),
                        staleClaimRequestId
                ))
                .isInstanceOfSatisfying(CustomException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("COMMON_004"));
    }

    @Test
    void cancelledAndCompletedRequestsDoNotExposeDetail() throws Exception {
        UserAccount cancelledOwner = createParamedic("cancelleddetailowner");
        String cancelledId = createRequest(cancelledOwner, "detail-request-key-004");
        var cancelled = requestRepository.findByPublicId(cancelledId).orElseThrow();
        cancelled.cancel(
                cancelledOwner,
                TransportCancellationReason.SCENE_RESOLVED,
                null,
                Instant.parse("2026-08-05T01:00:00Z")
        );
        requestRepository.flush();
        assertNotFound(cancelledId, cancelledOwner);

        UserAccount completedOwner = createParamedic("completeddetailowner");
        String completedId = createRequest(completedOwner, "detail-request-key-005");
        jdbcTemplate.update(
                "update transport_requests set status = 'COMPLETED', completed_at = ? where public_id = ?",
                Timestamp.from(Instant.parse("2026-08-05T01:01:00Z")),
                completedId
        );
        entityManager.clear();
        assertNotFound(completedId, completedOwner);
    }

    @Test
    void missingSnapshotUsesStandardInternalErrorWithoutPartialPatientData() throws Exception {
        UserAccount owner = createParamedic("missingsnapshotowner");
        String requestId = createRequest(owner, "detail-request-key-006");
        snapshotRepository.delete(snapshotRepository.findByTransportRequestPublicId(requestId).orElseThrow());
        snapshotRepository.flush();

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_003"))
                .andExpect(jsonPath("$.patient").doesNotExist())
                .andExpect(jsonPath("$.latestSnapshot").doesNotExist());
    }

    private void addVitalSigns(
            UserAccount owner,
            String requestId,
            String key,
            Instant measuredAt,
            String pulse
    ) throws Exception {
        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        var input = new UpdateVitalSignsRequest(
                measuredAt,
                measuredAt.plusSeconds(1),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(),
                        measurement.state(),
                        measurement.type().name().equals("PULSE") ? new BigDecimal(pulse) : measurement.primaryValue(),
                        measurement.secondaryValue(),
                        measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );
        mockMvc.perform(post(
                                "/api/v1/transport-requests/{requestId}/clinical-updates/vital-signs", requestId
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated());
    }

    private String createRequest(UserAccount owner, String key) throws Exception {
        String body = mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("transportRequestId").asText();
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = accountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                account, organization, loginId + " 대원", "010-0000-0001"
        ));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private UserAccount createMember(String loginId, OrganizationType type, UserRole role) {
        Organization organization = organizationRepository.save(Organization.create(loginId + " 조직", type));
        return accountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", role
        ));
    }

    private void assertNotFound(String requestId, UserAccount account) throws Exception {
        mockMvc.perform(get("/api/v1/transport-requests/{requestId}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_001"))
                .andExpect(jsonPath("$.patient").doesNotExist());
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
