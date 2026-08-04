package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.application.SearchOriginResolver;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.api.UpdateTransportLocationRequest;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportUpdateCommandRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class TransportLocationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository profileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestService requestService;
    @Autowired private TransportRequestRepository requestRepository;
    @Autowired private TransportCurrentLocationRepository locationRepository;
    @Autowired private TransportUpdateCommandRepository commandRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private RealtimeOutboxEventRepository outboxEventRepository;
    @Autowired private SearchOriginResolver searchOriginResolver;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void latestLocationOverwritesOneRowAndOldOrDuplicatePacketsDoNotRegressIt(CapturedOutput output)
            throws Exception {
        UserAccount owner = createParamedic("locationowner");
        UserAccount stranger = createParamedic("locationstranger");
        String requestId = createTransport(owner, "location-request-key");
        Instant capturedAt = Instant.now();
        var first = new UpdateTransportLocationRequest(
                new BigDecimal("37.5010000"), new BigDecimal("127.0010000"), capturedAt
        );

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshness").value("NOT_RECEIVED"))
                .andExpect(jsonPath("$.latitude").doesNotExist());

        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-update-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshness").value("CURRENT"))
                .andExpect(jsonPath("$.locationReplaced").value(true))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-update-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        var changed = new UpdateTransportLocationRequest(
                new BigDecimal("37.5020000"), new BigDecimal("127.0020000"), capturedAt.plusSeconds(1)
        );
        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-update-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));

        var older = new UpdateTransportLocationRequest(
                new BigDecimal("36.0000000"), new BigDecimal("126.0000000"), capturedAt.minusSeconds(1)
        );
        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-update-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(older)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationReplaced").value(false))
                .andExpect(jsonPath("$.latitude").value(37.501));

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_001"));

        assertThat(locationRepository.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(2);
        assertThat(auditEventRepository.countByAction(AuditAction.AMBULANCE_LOCATION_UPDATED)).isEqualTo(2);
        assertThat(outboxEventRepository.countByEventType(RealtimeEventType.AMBULANCE_LOCATION_UPDATED))
                .isEqualTo(1);
        assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> event.getEventType() == RealtimeEventType.AMBULANCE_LOCATION_UPDATED)
                .allSatisfy(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("TRANSPORT_REQUEST");
                    assertThat(event.getAggregatePublicId()).isEqualTo(requestId);
                });
        var request = requestRepository.findByPublicId(requestId).orElseThrow();
        var origin = searchOriginResolver.resolve(request);
        assertThat(origin.latitude()).isEqualByComparingTo("37.5010000");
        assertThat(origin.longitude()).isEqualByComparingTo("127.0010000");
        assertThat(output.getAll())
                .doesNotContain("37.5010000")
                .doesNotContain("127.0010000")
                .doesNotContain("36.0000000")
                .doesNotContain("126.0000000");
    }

    @Test
    void completedRequestStillReturnsAnAlreadyCommittedLocationReplay() throws Exception {
        UserAccount owner = createParamedic("locationclosedreplay");
        String requestId = createTransport(owner, "location-closed-replay-request");
        var input = new UpdateTransportLocationRequest(
                new BigDecimal("37.8111111"),
                new BigDecimal("127.8111111"),
                Instant.parse("2026-08-04T10:00:00Z")
        );

        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-closed-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        entityManager.flush();
        jdbcTemplate.update("UPDATE transport_requests SET status = 'COMPLETED' WHERE public_id = ?", requestId);
        entityManager.clear();

        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-closed-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertThat(locationRepository.count()).isEqualTo(1);
        assertThat(commandRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.AMBULANCE_LOCATION_UPDATED)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = TransportRequestStatus.class, names = {
            "SEARCHING", "CANDIDATES_EXHAUSTED", "ACCEPTED_AVAILABLE", "EN_ROUTE"
    })
    void everyActiveTransportStateAllowsLocationUpdates(TransportRequestStatus requestStatus) throws Exception {
        UserAccount owner = createParamedic("locationstate" + requestStatus.ordinal());
        String requestId = createTransport(owner, "location-state-request-" + requestStatus.ordinal());
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE transport_requests SET status = ? WHERE public_id = ?",
                requestStatus.name(),
                requestId
        );
        entityManager.clear();

        mockMvc.perform(put("/api/v1/transport-requests/{requestId}/location", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "location-state-key-" + requestStatus.ordinal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateTransportLocationRequest(
                                new BigDecimal("37.3000000"),
                                new BigDecimal("127.3000000"),
                                Instant.parse("2026-08-04T10:00:00Z")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationReplaced").value(true));
    }

    private String createTransport(UserAccount account, String key) {
        return requestService.create(
                new AuthenticatedAccount(
                        account.getPublicId(), account.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                key,
                ValidTransportRequestFixtures.request()
        ).response().transportRequestId();
    }

    private UserAccount createParamedic(String loginId) {
        Organization organization = organizationRepository.save(Organization.create(
                loginId + " 구급대", OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization, loginId, "encoded-password", UserRole.PARAMEDIC
        ));
        profileRepository.save(ParamedicProfile.create(account, organization, "010-0000-0001"));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
