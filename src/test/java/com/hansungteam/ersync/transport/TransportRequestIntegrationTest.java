package com.hansungteam.ersync.transport;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
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
import com.hansungteam.ersync.transport.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.IncidentAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.PatientDemographicsRepository;
import com.hansungteam.ersync.transport.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.transport.infrastructure.VitalSignSetRepository;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class TransportRequestIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private PatientDemographicsRepository patientDemographicsRepository;
    @Autowired private IncidentAssessmentRepository incidentAssessmentRepository;
    @Autowired private PreKtasAssessmentRepository preKtasAssessmentRepository;
    @Autowired private ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    @Autowired private VitalSignSetRepository vitalSignSetRepository;
    @Autowired private TreatmentEventRepository treatmentEventRepository;
    @Autowired private CurrentPatientSnapshotRepository currentPatientSnapshotRepository;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private TransportRequestService transportRequestService;

    @Test
    void paramedicCreatesSearchingRequestUsingServerOwnedContextAndClinicalRecords(CapturedOutput output) throws Exception {
        UserAccount paramedic = createParamedic("transportmedic", true);

        String response = mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "request-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/transport-requests/")))
                .andExpect(jsonPath("$.transportRequestId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.assessmentProtocolVersion").value("ERSYNC_MVP_1.0"))
                .andExpect(jsonPath("$.callbackContact").doesNotExist())
                .andExpect(jsonPath("$.origin").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String requestId = objectMapper.readTree(response).get("transportRequestId").asText();
        var stored = transportRequestRepository.findByPublicId(requestId).orElseThrow();
        assertThat(stored.getOwnerAccount().getPublicId()).isEqualTo(paramedic.getPublicId());
        assertThat(stored.getOrganization().getPublicId()).isEqualTo(paramedic.getOrganization().getPublicId());
        assertThat(stored.getCallbackContact()).isEqualTo("010-0000-0001");
        assertThat(patientDemographicsRepository.count()).isEqualTo(1);
        assertThat(incidentAssessmentRepository.count()).isEqualTo(1);
        assertThat(preKtasAssessmentRepository.count()).isEqualTo(1);
        assertThat(consciousnessAssessmentRepository.count()).isEqualTo(1);
        assertThat(vitalSignSetRepository.count()).isEqualTo(1);
        assertThat(treatmentEventRepository.count()).isEqualTo(1);
        assertThat(currentPatientSnapshotRepository.count()).isEqualTo(1);
        var snapshot = currentPatientSnapshotRepository.findByTransportRequestPublicId(requestId).orElseThrow();
        assertThat(snapshot.getPatientDemographics().getAgeYears()).isEqualTo(45);
        assertThat(snapshot.getIncidentAssessment().getPrimarySymptom().name()).isEqualTo("CHEST_PAIN");
        assertThat(snapshot.getAssessmentProtocolVersion()).isEqualTo("ERSYNC_MVP_1.0");
        assertThat(snapshot.getCurrentTreatments())
                .extracting(treatment -> treatment.getTreatmentType().name())
                .containsExactly("NONE");
        assertThat(snapshot.getLastClinicalUpdateAt()).isEqualTo(stored.getServerReceivedAt());
        assertThat(auditEventRepository.countByAction(AuditAction.TRANSPORT_REQUEST_CREATED)).isEqualTo(1);
        assertThat(output.getAll())
                .doesNotContain("010-0000-0001")
                .doesNotContain("37.5821000")
                .doesNotContain("127.0105000")
                .doesNotContain("CHEST_PAIN");
    }

    @Test
    void accountWithoutProfileOrConsentCannotCreateRequest() throws Exception {
        UserAccount paramedic = createParamedic("noprofilemedic", false);

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "request-key-0002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_005"));

        assertThat(transportRequestRepository.count()).isZero();
    }

    @Test
    void adminCannotCreatePatientRequest() throws Exception {
        UserAccount admin = userAccountRepository.save(UserAccount.createSuperAdmin(
                "transportadmin",
                "encoded-password"
        ));

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .header("Idempotency-Key", "request-key-0003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    void hospitalRoleAndMismatchedTokenOrganizationAreRejected() throws Exception {
        Organization hospital = organizationRepository.save(Organization.create(
                "요청 차단 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount hospitalAccount = userAccountRepository.save(UserAccount.createMember(
                hospital,
                "blockedhospital",
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalAccount))
                        .header("Idempotency-Key", "request-key-0005")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_003"));

        UserAccount paramedic = createParamedic("wrongorgmedic", true);
        AuthenticatedAccount mismatched = new AuthenticatedAccount(
                paramedic.getPublicId(),
                "00000000-0000-0000-0000-000000000000",
                UserRole.PARAMEDIC
        );
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> transportRequestService.create(
                mismatched,
                "request-key-0006",
                ValidTransportRequestFixtures.request()
        )))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode.code")
                .isEqualTo("COMMON_004");
    }

    @Test
    void missingAuthenticationAndInvalidIdempotencyKeyUseStandardErrors() throws Exception {
        mockMvc.perform(post("/api/v1/transport-requests")
                        .header("Idempotency-Key", "request-key-0004")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("AUTH_001"));

        UserAccount paramedic = createParamedic("badkeymedic", true);
        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "short")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ValidTransportRequestFixtures.request())))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.code").value("COMMON_001"));
    }

    @Test
    void nullElementsInClinicalArraysUseValidationErrorInsteadOfServerError() throws Exception {
        UserAccount paramedic = createParamedic("nullelementmedic", true);
        var base = ValidTransportRequestFixtures.request();
        List<com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.TreatmentInput> nullTreatments =
                new ArrayList<>();
        nullTreatments.add(null);
        var nullTreatmentRequest = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                base.assessmentProtocolVersion(),
                base.origin(),
                base.patient(),
                base.incident(),
                base.preKtas(),
                base.consciousness(),
                base.vitalSigns(),
                nullTreatments
        );

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "null-treatment-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullTreatmentRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        var nullMeasurements = new ArrayList<>(base.vitalSigns().measurements());
        nullMeasurements.set(0, null);
        var nullVitalRequest = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                base.assessmentProtocolVersion(),
                base.origin(),
                base.patient(),
                base.incident(),
                base.preKtas(),
                base.consciousness(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.VitalSignsInput(
                        base.vitalSigns().measuredAt(),
                        base.vitalSigns().enteredAt(),
                        nullMeasurements
                ),
                base.treatments()
        );

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "null-vital-key-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nullVitalRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        assertThat(transportRequestRepository.count()).isZero();
    }

    @Test
    void ageHasNoUnapprovedMaximumBeyondBeingNonNegative() throws Exception {
        UserAccount paramedic = createParamedic("agelimitmedic", true);
        var base = ValidTransportRequestFixtures.request();
        var request = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                base.assessmentProtocolVersion(),
                base.origin(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.PatientInput(
                        com.hansungteam.ersync.transport.domain.AgeStatus.EXACT,
                        131,
                        base.patient().sex()
                ),
                base.incident(),
                base.preKtas(),
                base.consciousness(),
                base.vitalSigns(),
                base.treatments()
        );

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "age-policy-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        assertThat(patientDemographicsRepository.findAll().getFirst().getAgeYears()).isEqualTo(131);
    }

    @Test
    void identicalIdempotentRetryReturnsOriginalWithoutDuplicateRecords() throws Exception {
        UserAccount paramedic = createParamedic("retrymedic", true);
        String payload = objectMapper.writeValueAsString(ValidTransportRequestFixtures.request());

        String first = mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "retry-key-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String retry = mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "retry-key-000001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(retry).get("transportRequestId").asText())
                .isEqualTo(objectMapper.readTree(first).get("transportRequestId").asText());
        assertThat(transportRequestRepository.count()).isEqualTo(1);
        assertThat(preKtasAssessmentRepository.count()).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.TRANSPORT_REQUEST_CREATED)).isEqualTo(1);
    }

    @Test
    void changedPayloadWithSameIdempotencyKeyIsRejected() throws Exception {
        UserAccount paramedic = createParamedic("conflictmedic", true);
        var original = ValidTransportRequestFixtures.request();
        var changed = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                original.assessmentProtocolVersion(),
                original.origin(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.PatientInput(
                        original.patient().ageStatus(),
                        46,
                        original.patient().sex()
                ),
                original.incident(),
                original.preKtas(),
                original.consciousness(),
                original.vitalSigns(),
                original.treatments()
        );

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "conflict-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(original)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "conflict-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changed)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON_005"));

        assertThat(transportRequestRepository.count()).isEqualTo(1);
        assertThat(patientDemographicsRepository.findAll().getFirst().getAgeYears()).isEqualTo(45);
    }

    @Test
    void emergencyExceptionUnavailableVitalsAndOxygenTreatmentPersistTogether() throws Exception {
        UserAccount paramedic = createParamedic("emergencymedic", true);
        var base = ValidTransportRequestFixtures.request();
        var measurements = new ArrayList<>(base.vitalSigns().measurements());
        measurements.set(1, new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.VitalSignInput(
                com.hansungteam.ersync.transport.domain.VitalSignType.PULSE,
                com.hansungteam.ersync.transport.domain.VitalSignState.MEASUREMENT_UNAVAILABLE,
                null,
                null,
                com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason.DEVICE_ERROR,
                null
        ));
        measurements.set(2, new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.VitalSignInput(
                com.hansungteam.ersync.transport.domain.VitalSignType.RESPIRATORY_RATE,
                com.hansungteam.ersync.transport.domain.VitalSignState.PATIENT_REFUSED,
                null,
                null,
                null,
                null
        ));
        var emergency = new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest(
                base.assessmentProtocolVersion(),
                base.origin(),
                base.patient(),
                base.incident(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.PreKtasInput(
                        com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus.EMERGENCY_UNFINISHED,
                        null,
                        com.hansungteam.ersync.transport.domain.PreKtasExceptionReason.CPR_IN_PROGRESS,
                        null,
                        null,
                        "DEV_UNCONFIRMED",
                        base.preKtas().enteredAt()
                ),
                base.consciousness(),
                new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.VitalSignsInput(
                        base.vitalSigns().measuredAt(),
                        base.vitalSigns().enteredAt(),
                        measurements
                ),
                List.of(new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.TreatmentInput(
                        com.hansungteam.ersync.transport.domain.TreatmentType.OXYGEN,
                        com.hansungteam.ersync.transport.domain.TreatmentAttemptResult.SUCCESS,
                        new com.hansungteam.ersync.transport.api.CreateTransportRequestRequest.TreatmentDetailsInput(
                                "nasal-cannula", null, new BigDecimal("4"), null, null, null,
                                null, null, null, null, null, null, null, null, null, null,
                                null, null, null, null, null, null
                        ),
                        Instant.parse("2026-08-03T10:00:30Z"),
                        Instant.parse("2026-08-03T10:01:00Z")
                ))
        );

        mockMvc.perform(post("/api/v1/transport-requests")
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "emergency-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emergency)))
                .andExpect(status().isCreated());

        assertThat(preKtasAssessmentRepository.findAll().getFirst().getClassificationStatus())
                .isEqualTo(com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus.EMERGENCY_UNFINISHED);
        assertThat(vitalSignSetRepository.findAll().getFirst().getMeasurements())
                .extracting(measurement -> measurement.getState().name())
                .contains("MEASUREMENT_UNAVAILABLE", "PATIENT_REFUSED");
        assertThat(treatmentEventRepository.findAll().getFirst().getDetails().getFlowRateLpm())
                .isEqualByComparingTo("4");
    }

    private UserAccount createParamedic(String loginId, boolean withProfileAndConsent) {
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
        if (withProfileAndConsent) {
            paramedicProfileRepository.save(ParamedicProfile.create(
                    account,
                    organization,
                    "010-0000-0001"
            ));
            consentRepository.save(ContactSharingConsent.record(
                    account,
                    "CONTACT_SHARING_DEV_1.0",
                    Instant.parse("2026-08-03T09:00:00Z")
            ));
        }
        return account;
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
