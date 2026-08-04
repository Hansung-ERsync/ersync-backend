package com.hansungteam.ersync.hospital.search;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferEventRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HospitalSearchApiIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private HospitalOfferEventRepository offerEventRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private JwtTokenService jwtTokenService;

    @Test
    void hospitalReadsOnlyItsOfferAndTwoHospitalsCanAccept() throws Exception {
        UserAccount paramedic = createParamedic("apimedic1");
        UserAccount hospitalOne = createHospital("apihospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("apihospital2", "37.6221000");
        String requestId = createAndSearch(paramedic, "api-search-request-1");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var hospitalOneOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getPublicId()
                        .equals(hospitalOne.getPublicId()))
                .findFirst()
                .orElseThrow();
        var hospitalTwoOffer = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getPublicId()
                        .equals(hospitalTwo.getPublicId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].offerId").value(hospitalOneOffer.getPublicId()));

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", hospitalOneOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.callbackContact").value("010-0000-0001"))
                .andExpect(jsonPath("$.patient.ageYears").value(45))
                .andExpect(jsonPath("$.originLatitude").doesNotExist());

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", hospitalOneOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", hospitalOneOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne))
                        .header("Idempotency-Key", "accept-api-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.transportRequestStatus").value("ACCEPTED_AVAILABLE"))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", hospitalOneOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne))
                        .header("Idempotency-Key", "accept-api-key-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/accept", hospitalTwoOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo))
                        .header("Idempotency-Key", "accept-api-key-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("ACCEPTED"));

        assertThat(offerEventRepository.findByHospitalOfferDispatchAttemptIdOrderByOccurredAtAsc(
                hospitalOneOffer.getDispatchAttempt().getId()
        )).filteredOn(event -> event.getEventType().name().equals("ACCEPTED")).hasSize(2);
        assertThat(attemptRepository.findById(hospitalOneOffer.getDispatchAttempt().getId()).orElseThrow()
                .getStatus()).isEqualTo(HospitalDispatchAttemptStatus.STOPPED_ON_ACCEPTANCE);
    }

    @Test
    void finalRejectionExhaustsRequestAndParamedicRetryIsIdempotent() throws Exception {
        UserAccount paramedic = createParamedic("apimedic2");
        UserAccount hospital = createHospital("apihospital3", "37.6021000");
        String requestId = createAndSearch(paramedic, "api-search-request-2");
        var offer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).getFirst();

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/reject", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "reject-api-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "OTHER"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"));

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/reject", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital))
                        .header("Idempotency-Key", "reject-api-key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "ER_GENERAL_BED_SHORTAGE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.transportRequestStatus").value("CANDIDATES_EXHAUSTED"));

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requester.callbackContact").value("****-0001"));

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANDIDATES_EXHAUSTED"))
                .andExpect(jsonPath("$.exhaustionReason").value("ALL_REJECTED"))
                .andExpect(jsonPath("$.offers[0].hospitalContact").value("02-0000-0000"));

        String firstRetry = mockMvc.perform(post(
                                "/api/v1/transport-requests/{requestId}/dispatch-attempts",
                                requestId
                        )
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "retry-api-key-01"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attemptNumber").value(2))
                .andExpect(jsonPath("$.transportRequestStatus").value("SEARCHING"))
                .andReturn().getResponse().getContentAsString();
        String attemptId = objectMapper.readTree(firstRetry).get("dispatchAttemptId").asText();

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/dispatch-attempts", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "retry-api-key-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchAttemptId").value(attemptId))
                .andExpect(jsonPath("$.idempotentReplay").value(true));

        assertThat(attemptRepository.findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow().getAttemptNumber()).isEqualTo(2);
        assertThat(transportRequestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.SEARCHING);
    }

    private String createAndSearch(UserAccount paramedic, String idempotencyKey) {
        var creation = transportRequestService.create(
                new AuthenticatedAccount(
                        paramedic.getPublicId(),
                        paramedic.getOrganization().getPublicId(),
                        UserRole.PARAMEDIC
                ),
                idempotencyKey,
                ValidTransportRequestFixtures.request()
        );
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(
                creation.response().transportRequestId(),
                1
        ).orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        return creation.response().transportRequestId();
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
                "010-0000-0001"
        ));
        consentRepository.save(ContactSharingConsent.record(
                account,
                "CONTACT_SHARING_DEV_1.0",
                Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private UserAccount createHospital(String loginId, String latitude) {
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
        HospitalProfile profile = HospitalProfile.create(
                organization,
                account,
                "서울특별시 테스트 주소",
                new BigDecimal(latitude),
                new BigDecimal("127.0105000"),
                "02-0000-0000"
        );
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
