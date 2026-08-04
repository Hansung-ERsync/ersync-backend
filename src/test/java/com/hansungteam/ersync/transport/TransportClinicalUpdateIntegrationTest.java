package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.api.UpdateConsciousnessRequest;
import com.hansungteam.ersync.transport.api.UpdatePreKtasRequest;
import com.hansungteam.ersync.transport.api.UpdateTreatmentRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportUpdateCommandRepository;
import com.hansungteam.ersync.transport.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.transport.infrastructure.VitalSignSetRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransportClinicalUpdateIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private VitalSignSetRepository vitalSignSetRepository;
    @Autowired private ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    @Autowired private PreKtasAssessmentRepository preKtasAssessmentRepository;
    @Autowired private TreatmentEventRepository treatmentEventRepository;
    @Autowired private CurrentPatientSnapshotRepository snapshotRepository;
    @Autowired private TransportUpdateCommandRepository commandRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    void allFourClinicalTypesAppendRecordsAndAdvanceTheSnapshot() throws Exception {
        UserAccount paramedic = createParamedic("clinicalall");
        String requestId = createTransport(paramedic, "clinical-request-001");
        var initial = ValidTransportRequestFixtures.request();

        UpdateVitalSignsRequest vitalSigns = vitalRequest(initial.vitalSigns().measuredAt().plusSeconds(30));
        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "clinical-vital-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(vitalSigns)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.updateType").value("VITAL_SIGNS"))
                .andExpect(jsonPath("$.snapshotUpdated").value(true))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        var consciousness = new UpdateConsciousnessRequest(
                Avpu.V, null, null,
                initial.consciousness().observedAt().plusSeconds(40),
                initial.consciousness().enteredAt().plusSeconds(40)
        );
        postCreated(paramedic, requestId, "consciousness", "clinical-conscious-001", consciousness);

        var preKtas = new UpdatePreKtasRequest(
                PreKtasClassificationStatus.COMPLETED,
                2,
                null,
                null,
                initial.preKtas().assessedAt().plusSeconds(50),
                "DEV_UNCONFIRMED",
                initial.preKtas().enteredAt().plusSeconds(50)
        );
        postCreated(paramedic, requestId, "pre-ktas", "clinical-prektas-001", preKtas);

        var treatment = treatmentRequest(initial.treatments().getFirst().enteredAt().plusSeconds(60));
        postCreated(paramedic, requestId, "treatments", "clinical-treatment-001", treatment);

        assertThat(vitalSignSetRepository.count()).isEqualTo(2);
        assertThat(consciousnessAssessmentRepository.count()).isEqualTo(2);
        assertThat(preKtasAssessmentRepository.count()).isEqualTo(2);
        assertThat(treatmentEventRepository.count()).isEqualTo(2);
        assertThat(commandRepository.count()).isEqualTo(4);
        assertThat(auditEventRepository.countByAction(AuditAction.VITAL_SIGNS_ADDED)).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.CONSCIOUSNESS_CHANGED)).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.PRE_KTAS_CHANGED)).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.TREATMENT_ADDED)).isEqualTo(1);
        assertThat(auditEventRepository.findAll())
                .filteredOn(event -> event.getAction() == AuditAction.TREATMENT_ADDED)
                .allSatisfy(event -> {
                    assertThat(event.getTargetType()).isEqualTo("TREATMENT");
                    assertThat(event.getTargetPublicId()).hasSize(36);
                });

        entityManager.flush();
        entityManager.clear();
        var snapshot = snapshotRepository.findByTransportRequestPublicId(requestId).orElseThrow();
        assertThat(snapshot.getLatestVitalSignSet().getMeasuredAt()).isEqualTo(vitalSigns.measuredAt());
        assertThat(snapshot.getLatestConsciousnessAssessment().getAvpu()).isEqualTo(Avpu.V);
        assertThat(snapshot.getLatestPreKtasAssessment().getLevel()).isEqualTo(2);
        assertThat(snapshot.getCurrentTreatments())
                .singleElement()
                .satisfies(current -> assertThat(current.getTreatmentType()).isEqualTo(TreatmentType.OTHER));
    }

    @Test
    void replayReturnsOriginalAndLateOlderRecordDoesNotRegressSnapshot() throws Exception {
        UserAccount paramedic = createParamedic("clinicalreplay");
        String requestId = createTransport(paramedic, "clinical-request-002");
        Instant initialMeasuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt();
        var latest = vitalRequest(initialMeasuredAt.plusSeconds(60));

        postCreated(paramedic, requestId, "vital-signs", "clinical-replay-001", latest);
        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "clinical-replay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(latest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        var changed = vitalRequest(initialMeasuredAt.plusSeconds(61));
        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "clinical-replay-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));

        var older = vitalRequest(initialMeasuredAt.minusSeconds(1));
        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "clinical-older-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(older)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.snapshotUpdated").value(false));

        assertThat(vitalSignSetRepository.count()).isEqualTo(3);
        assertThat(auditEventRepository.countByAction(AuditAction.VITAL_SIGNS_ADDED)).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();
        assertThat(snapshotRepository.findByTransportRequestPublicId(requestId).orElseThrow()
                .getLatestVitalSignSet().getMeasuredAt()).isEqualTo(latest.measuredAt());
    }

    @Test
    void anotherParamedicCannotUpdateAndClosedStateIsRejected() throws Exception {
        UserAccount owner = createParamedic("clinicalowner");
        UserAccount stranger = createParamedic("clinicalother");
        String requestId = createTransport(owner, "clinical-request-003");
        var input = vitalRequest(ValidTransportRequestFixtures.request().vitalSigns().measuredAt().plusSeconds(30));

        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger))
                        .header("Idempotency-Key", "clinical-owner-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_001"));

        jdbcTemplate.update("UPDATE transport_requests SET status = 'COMPLETED' WHERE public_id = ?", requestId);
        entityManager.clear();

        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "clinical-closed-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSPORT_004"));

        assertThat(commandRepository.count()).isZero();
    }

    @Test
    void completedRequestStillReturnsAnAlreadyCommittedClinicalReplay() throws Exception {
        UserAccount owner = createParamedic("clinicalclosedreplay");
        String requestId = createTransport(owner, "clinical-request-closed-replay");
        var input = vitalRequest(ValidTransportRequestFixtures.request().vitalSigns().measuredAt().plusSeconds(30));

        postCreated(owner, requestId, "vital-signs", "clinical-closed-replay", input);
        jdbcTemplate.update("UPDATE transport_requests SET status = 'COMPLETED' WHERE public_id = ?", requestId);
        entityManager.clear();

        mockMvc.perform(post(path(requestId, "vital-signs"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .header("Idempotency-Key", "clinical-closed-replay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertThat(vitalSignSetRepository.count()).isEqualTo(2);
        assertThat(auditEventRepository.countByAction(AuditAction.VITAL_SIGNS_ADDED)).isEqualTo(1);
    }

    @ParameterizedTest
    @EnumSource(value = TransportRequestStatus.class, names = {
            "SEARCHING", "CANDIDATES_EXHAUSTED", "ACCEPTED_AVAILABLE", "EN_ROUTE"
    })
    void everyActiveTransportStateAllowsClinicalUpdates(TransportRequestStatus requestStatus) throws Exception {
        UserAccount owner = createParamedic("clinicalstate" + requestStatus.ordinal());
        String requestId = createTransport(owner, "clinical-state-request-" + requestStatus.ordinal());
        entityManager.flush();
        jdbcTemplate.update(
                "UPDATE transport_requests SET status = ? WHERE public_id = ?",
                requestStatus.name(),
                requestId
        );
        entityManager.clear();

        postCreated(
                owner,
                requestId,
                "vital-signs",
                "clinical-state-key-" + requestStatus.ordinal(),
                vitalRequest(ValidTransportRequestFixtures.request().vitalSigns().measuredAt().plusSeconds(30))
        );
    }

    @Test
    void ownerReadsStableClinicalTimelinePagesButAnotherParamedicCannot() throws Exception {
        UserAccount owner = createParamedic("clinicaltimelineowner");
        UserAccount stranger = createParamedic("clinicaltimelinestranger");
        String requestId = createTransport(owner, "clinical-request-004");
        Instant initialMeasuredAt = ValidTransportRequestFixtures.request().vitalSigns().measuredAt();
        postCreated(
                owner, requestId, "vital-signs", "clinical-timeline-001",
                vitalRequest(initialMeasuredAt.plusSeconds(60))
        );
        postCreated(
                owner, requestId, "treatments", "clinical-timeline-002",
                treatmentRequest(initialMeasuredAt.plusSeconds(70))
        );

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/clinical-timeline", requestId)
                        .queryParam("page", "0")
                        .queryParam("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.latestSnapshot.vitalSigns.measuredAt")
                        .value(initialMeasuredAt.plusSeconds(60).toString()))
                .andExpect(jsonPath("$.latestSnapshot.treatments.length()").value(1))
                .andExpect(jsonPath("$.latestSnapshot.treatments[0].type").value("OTHER"));

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/clinical-timeline", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(stranger)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_001"));

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/clinical-timeline", requestId)
                        .queryParam("page", String.valueOf(Integer.MAX_VALUE))
                        .queryParam("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    private void postCreated(
            UserAccount account,
            String requestId,
            String suffix,
            String key,
            Object input
    ) throws Exception {
        mockMvc.perform(post(path(requestId, suffix))
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(input)))
                .andExpect(status().isCreated());
    }

    private UpdateVitalSignsRequest vitalRequest(Instant measuredAt) {
        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        return new UpdateVitalSignsRequest(
                measuredAt,
                measuredAt.plusSeconds(1),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(), measurement.state(), measurement.primaryValue(),
                        measurement.secondaryValue(), measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );
    }

    private UpdateTreatmentRequest treatmentRequest(Instant enteredAt) {
        return new UpdateTreatmentRequest(
                TreatmentType.OTHER,
                TreatmentAttemptResult.FAILURE,
                new UpdateTreatmentRequest.TreatmentDetailsInput(
                        null, null, null, null, false, null, null, null, null, null, null,
                        null, null, null, null, null, null, null, null, null, null, "failed attempt"
                ),
                enteredAt.minusSeconds(1),
                enteredAt
        );
    }

    private String createTransport(UserAccount account, String key) {
        return transportRequestService.create(
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
        paramedicProfileRepository.save(ParamedicProfile.create(account, organization, "010-0000-0001"));
        consentRepository.save(ContactSharingConsent.record(
                account, "CONTACT_SHARING_DEV_1.0", Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private String path(String requestId, String suffix) {
        return "/api/v1/transport-requests/" + requestId + "/clinical-updates/" + suffix;
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
