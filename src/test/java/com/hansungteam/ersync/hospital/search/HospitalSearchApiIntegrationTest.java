package com.hansungteam.ersync.hospital.search;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.auth.application.JwtTokenService;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.application.HospitalOfferService;
import com.hansungteam.ersync.hospital.search.application.RouteEstimate;
import com.hansungteam.ersync.hospital.search.application.RouteEstimatePersistence;
import com.hansungteam.ersync.hospital.search.api.TransportHospitalSearchResponse;
import com.hansungteam.ersync.hospital.search.api.WithdrawHospitalAcceptanceRequest;
import com.hansungteam.ersync.hospital.search.domain.HospitalAcceptanceWithdrawalReason;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus;
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
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.application.TransportClinicalUpdateService;
import com.hansungteam.ersync.transport.application.TransportLocationService;
import com.hansungteam.ersync.transport.api.UpdateTransportLocationRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.destination.application.TransportDestinationService;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.ClinicalTimelineRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import tools.jackson.databind.ObjectMapper;

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
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private HospitalOfferEventRepository offerEventRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private ClinicalTimelineRepository clinicalTimelineRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private HospitalOfferService hospitalOfferService;
    @Autowired private TransportDestinationService destinationService;
    @Autowired private TransportClinicalUpdateService clinicalUpdateService;
    @Autowired private TransportLocationService transportLocationService;
    @Autowired private RouteEstimatePersistence routeEstimatePersistence;
    @Autowired private AuditEventRepository auditEventRepository;
    @Autowired private RealtimeOutboxEventRepository outboxEventRepository;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private ObjectMapper objectMapper;

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
                .andExpect(jsonPath("$.items[0].offerId").value(hospitalOneOffer.getPublicId()))
                .andExpect(jsonPath("$.items[0].reRequested").value(false))
                .andExpect(jsonPath("$.items[0].canConfirmHandoff").doesNotExist())
                .andExpect(jsonPath("$.items[0].lastRequestedAt").value(
                        hospitalOneOffer.getOfferedAt().toString()
                ))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist())
                .andExpect(jsonPath("$.serverNow").doesNotExist());

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", hospitalOneOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportRequestId").doesNotExist())
                .andExpect(jsonPath("$.hospitalOutcome").doesNotExist())
                .andExpect(jsonPath("$.processedAt").doesNotExist())
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.cancelledAt").doesNotExist())
                .andExpect(jsonPath("$.cancellationReason").doesNotExist())
                .andExpect(jsonPath("$.requester.callbackContact").value("010-0000-0001"))
                .andExpect(jsonPath("$.patient.ageYears").value(45))
                .andExpect(jsonPath("$.preKtas.exceptionDetail").doesNotExist())
                .andExpect(jsonPath("$.preKtas.assessedAt").doesNotExist())
                .andExpect(jsonPath("$.preKtas.standardVersion").doesNotExist())
                .andExpect(jsonPath("$.consciousness.unassessableDetail").doesNotExist())
                .andExpect(jsonPath("$.consciousness.observedAt").doesNotExist())
                .andExpect(jsonPath("$.route.calculatedAt").doesNotExist())
                .andExpect(jsonPath("$.timing.offeredAt").doesNotExist())
                .andExpect(jsonPath("$.timing.reRequested").value(false))
                .andExpect(jsonPath("$.timing.lastRequestedAt").value(
                        hospitalOneOffer.getOfferedAt().toString()
                ))
                .andExpect(jsonPath("$.serverNow").isNotEmpty())
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
    void paramedicReadsSnapshotAddressOnlyForAcceptedHospitals() throws Exception {
        UserAccount paramedic = createParamedic("addressmedic");
        UserAccount hospitalOne = createHospital(
                "addresshospital1",
                "서울특별시 성북구 첫번째로 1",
                "본관 1층 응급의료센터",
                "37.6021000"
        );
        UserAccount hospitalTwo = createHospital(
                "addresshospital2",
                "서울특별시 성북구 두번째로 2",
                "별관 지하 1층 구급차 진입구",
                "37.6121000"
        );
        String requestId = createAndSearch(paramedic, "address-search-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst()
                .orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(offerOne.getHospitalAddressSnapshot()).isEqualTo("서울특별시 성북구 첫번째로 1");
        assertThat(offerOne.getHospitalDetailAddressSnapshot()).isEqualTo("본관 1층 응급의료센터");
        assertThat(offerOne.getHospitalLatitudeSnapshot()).isEqualByComparingTo("37.6021000");

        var pending = readHospitalSearch(paramedic, requestId);
        assertHospitalLocationHidden(findOffer(pending, offerOne.getPublicId()));
        assertHospitalLocationHidden(findOffer(pending, offerTwo.getPublicId()));

        hospitalOfferService.accept(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "address-accept-one"
        );
        var oneAccepted = readHospitalSearch(paramedic, requestId);
        assertHospitalLocation(
                findOffer(oneAccepted, offerOne.getPublicId()),
                "서울특별시 성북구 첫번째로 1",
                "본관 1층 응급의료센터",
                "37.6021000"
        );
        assertHospitalLocationHidden(findOffer(oneAccepted, offerTwo.getPublicId()));

        hospitalOfferService.accept(
                hospitalPrincipal(hospitalTwo),
                offerTwo.getPublicId(),
                "address-accept-two"
        );
        AuthenticatedAccount paramedicPrincipal = new AuthenticatedAccount(
                paramedic.getPublicId(),
                paramedic.getOrganization().getPublicId(),
                UserRole.PARAMEDIC
        );
        destinationService.select(
                paramedicPrincipal,
                requestId,
                "address-destination-one",
                offerOne.getPublicId()
        );
        destinationService.select(
                paramedicPrincipal,
                requestId,
                "address-destination-two",
                offerTwo.getPublicId()
        );

        var changedDestination = readHospitalSearch(paramedic, requestId);
        var selected = findOffer(changedDestination, offerTwo.getPublicId());
        assertThat(selected.currentDestination()).isTrue();
        assertHospitalLocation(
                selected,
                "서울특별시 성북구 두번째로 2",
                "별관 지하 1층 구급차 진입구",
                "37.6121000"
        );

        hospitalOfferService.withdrawAcceptance(
                hospitalPrincipal(hospitalOne),
                offerOne.getPublicId(),
                "address-withdraw-one",
                new WithdrawHospitalAcceptanceRequest(
                        HospitalAcceptanceWithdrawalReason.BED_SHORTAGE,
                        null
                )
        );
        var afterWithdrawal = readHospitalSearch(paramedic, requestId);
        assertHospitalLocationHidden(findOffer(afterWithdrawal, offerOne.getPublicId()));
        assertHospitalLocation(
                findOffer(afterWithdrawal, offerTwo.getPublicId()),
                "서울특별시 성북구 두번째로 2",
                "별관 지하 1층 구급차 진입구",
                "37.6121000"
        );
    }

    @Test
    void hospitalReadsSupplementalAssessmentOnlyWhileClinicalAccessRemains() throws Exception {
        UserAccount paramedic = createParamedic("supplementalhospitalmedic");
        UserAccount destinationHospital = createHospital("supplementaldestination", "37.6021000");
        UserAccount rejectedHospital = createHospital("supplementalrejected", "37.6121000");
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
                        com.hansungteam.ersync.transport.domain.PupilResponse.NORMAL,
                        "고혈압",
                        null,
                        null,
                        true
                )
        );
        String requestId = createAndSearch(paramedic, "supplemental-hospital-request", request);
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var destinationOffer = offers.stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId()
                        .equals(destinationHospital.getId()))
                .findFirst()
                .orElseThrow();
        var rejectedOffer = offers.stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId()
                        .equals(rejectedHospital.getId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", destinationOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(destinationHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplementalAssessment.glucoseMgDl").value(85))
                .andExpect(jsonPath("$.supplementalAssessment.medicalHistory").value("고혈압"))
                .andExpect(jsonPath("$.supplementalAssessment.isolationConcern").value(true));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(destinationHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].supplementalAssessment").doesNotExist());

        mockMvc.perform(post("/api/v1/hospitals/me/offers/{offerId}/reject", rejectedOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectedHospital))
                        .header("Idempotency-Key", "supplemental-reject-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason": "ER_GENERAL_BED_SHORTAGE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", rejectedOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(rejectedHospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        hospitalOfferService.accept(
                new AuthenticatedAccount(
                        destinationHospital.getPublicId(),
                        destinationHospital.getOrganization().getPublicId(),
                        UserRole.HOSPITAL_STAFF
                ),
                destinationOffer.getPublicId(),
                "supplemental-destination-accept"
        );
        destinationService.select(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "supplemental-destination-select",
                destinationOffer.getPublicId()
        );

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", destinationOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(destinationHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplementalAssessment.glucoseMgDl").value(85));
    }

    @Test
    void finalRejectionKeepsSearchActiveAndManualRetryRouteIsUnavailable() throws Exception {
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
                                  "reason": "OTHER",
                                  "detail": "Local treatment unavailable"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.transportRequestStatus").value("SEARCHING"));

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        var initialVitals = ValidTransportRequestFixtures.request().vitalSigns();
        var updateAfterRejection = new UpdateVitalSignsRequest(
                initialVitals.measuredAt().plusSeconds(60),
                initialVitals.enteredAt().plusSeconds(60),
                initialVitals.measurements().stream().map(measurement ->
                        new UpdateVitalSignsRequest.VitalSignInput(
                                measurement.type(), measurement.state(), measurement.primaryValue(),
                                measurement.secondaryValue(), measurement.unavailableReason(),
                                measurement.unavailableDetail()
                        )).toList()
        );
        clinicalUpdateService.addVitalSigns(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "rejected-offer-hidden-clinical-update",
                updateAfterRejection
        );

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", offer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .param("view", "HISTORY")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].offerId").value(offer.getPublicId()))
                .andExpect(jsonPath("$.items[0].offerStatus").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].hospitalOutcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].rejectionReason").value("OTHER"))
                .andExpect(jsonPath("$.items[0].rejectionDetail")
                        .value("Local treatment unavailable"))
                .andExpect(jsonPath("$.items[0].processedAt").exists())
                .andExpect(jsonPath("$.items[0].ageStatus").doesNotExist())
                .andExpect(jsonPath("$.items[0].preKtasLevel").doesNotExist())
                .andExpect(jsonPath("$.items[0].lastClinicalUpdateAt").doesNotExist())
                .andExpect(jsonPath("$.items[0].straightLineDistanceMeters").doesNotExist())
                .andExpect(jsonPath("$.items[0].etaSeconds").doesNotExist());

        mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SEARCHING"))
                .andExpect(jsonPath("$.exhaustionReason").doesNotExist())
                .andExpect(jsonPath("$.offers[0].hospitalContact").doesNotExist())
                .andExpect(jsonPath("$.offers[0].hospitalAddress").doesNotExist())
                .andExpect(jsonPath("$.offers[0].hospitalDetailAddress").doesNotExist())
                .andExpect(jsonPath("$.offers[0].hospitalLatitude").doesNotExist())
                .andExpect(jsonPath("$.offers[0].hospitalLongitude").doesNotExist());

        mockMvc.perform(post("/api/v1/transport-requests/{requestId}/dispatch-attempts", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic))
                        .header("Idempotency-Key", "retry-api-key-01"))
                .andExpect(status().isNotFound());

        assertThat(attemptRepository.findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow().getAttemptNumber()).isEqualTo(1);
        assertThat(transportRequestRepository.findByPublicId(requestId).orElseThrow().getStatus())
                .isEqualTo(TransportRequestStatus.SEARCHING);
    }

    @Test
    void candidateHospitalsReadUpdatesUntilDestinationSelectionThenOnlyDestinationCanRead() throws Exception {
        UserAccount paramedic = createParamedic("timelineparamedic");
        UserAccount hospitalOne = createHospital("timelinehospital1", "37.6021000");
        UserAccount hospitalTwo = createHospital("timelinehospital2", "37.6121000");
        String requestId = createAndSearch(paramedic, "timeline-search-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var offerOne = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalOne.getId()))
                .findFirst().orElseThrow();
        var offerTwo = offers.stream()
                .filter(offer -> offer.getHospitalProfile().getAccount().getId().equals(hospitalTwo.getId()))
                .findFirst().orElseThrow();

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(4));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk());

        var initial = ValidTransportRequestFixtures.request().vitalSigns();
        var update = new UpdateVitalSignsRequest(
                initial.measuredAt().plusSeconds(60),
                initial.enteredAt().plusSeconds(60),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(), measurement.state(), measurement.primaryValue(),
                        measurement.secondaryValue(), measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );
        clinicalUpdateService.addVitalSigns(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "timeline-clinical-key",
                update
        );
        transportLocationService.update(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "timeline-location-key",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7000000"),
                        new BigDecimal("127.2000000"),
                        Instant.now()
                )
        );

        assertThat(auditEventRepository.countByAction(AuditAction.VITAL_SIGNS_ADDED)).isEqualTo(1);
        assertThat(auditEventRepository.countByAction(AuditAction.AMBULANCE_LOCATION_UPDATED)).isEqualTo(1);
        assertThat(outboxEventRepository.countByEventType(RealtimeEventType.VITAL_SIGNS_ADDED)).isEqualTo(2);
        assertThat(outboxEventRepository.countByEventType(RealtimeEventType.AMBULANCE_LOCATION_UPDATED))
                .isEqualTo(1);
        assertThat(outboxEventRepository.findAll())
                .filteredOn(event -> event.getEventType() == RealtimeEventType.VITAL_SIGNS_ADDED)
                .allSatisfy(event -> {
                    assertThat(event.getAggregateType()).isEqualTo("TRANSPORT_REQUEST");
                    assertThat(event.getAggregatePublicId()).isEqualTo(requestId);
                    assertThat(event.getAudiencePublicId())
                            .isIn(hospitalOne.getOrganization().getPublicId(),
                                    hospitalTwo.getOrganization().getPublicId());
                });

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        mockMvc.perform(get("/api/v1/hospitals/me/offers")
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].lastClinicalUpdateAt").isNotEmpty());

        hospitalOfferService.accept(
                new AuthenticatedAccount(
                        hospitalOne.getPublicId(), hospitalOne.getOrganization().getPublicId(),
                        UserRole.HOSPITAL_STAFF
                ),
                offerOne.getPublicId(),
                "timeline-accept-one"
        );
        hospitalOfferService.accept(
                new AuthenticatedAccount(
                        hospitalTwo.getPublicId(), hospitalTwo.getOrganization().getPublicId(),
                        UserRole.HOSPITAL_STAFF
                ),
                offerTwo.getPublicId(),
                "timeline-accept-two"
        );
        destinationService.select(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "timeline-destination-key",
                offerOne.getPublicId()
        );
        Instant nonDestinationCutoff = offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt();
        assertThat(nonDestinationCutoff).isNotNull();

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.7))
                .andExpect(jsonPath("$.freshness").value("CURRENT"));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        var laterUpdate = new UpdateVitalSignsRequest(
                initial.measuredAt().plusSeconds(120),
                initial.enteredAt().plusSeconds(120),
                initial.measurements().stream().map(measurement -> new UpdateVitalSignsRequest.VitalSignInput(
                        measurement.type(), measurement.state(), measurement.primaryValue(),
                        measurement.secondaryValue(), measurement.unavailableReason(),
                        measurement.unavailableDetail()
                )).toList()
        );
        var laterResult = clinicalUpdateService.addVitalSigns(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "timeline-clinical-key-after-destination",
                laterUpdate
        );
        assertThat(laterResult.response().serverReceivedAt()).isAfter(nonDestinationCutoff);
        assertThat(offerRepository.findById(offerTwo.getId()).orElseThrow()
                .getClinicalVisibilityCutoffAt()).isEqualTo(nonDestinationCutoff);
        assertThat(clinicalTimelineRepository.count(
                transportRequestRepository.findByPublicId(requestId).orElseThrow().getId(),
                nonDestinationCutoff
        )).isEqualTo(5);

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offerOne.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.latestSnapshot.vitalSigns.measuredAt")
                        .value(laterUpdate.measuredAt().toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/clinical-timeline", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.latestSnapshot.vitalSigns.measuredAt")
                        .value(update.measuredAt().toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", offerTwo.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(hospitalTwo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vitalSigns.measuredAt").value(update.measuredAt().toString()))
                .andExpect(jsonPath("$.route.status").doesNotExist());

        Instant routeNow = Instant.now().plusSeconds(1);
        var oldGeneration = routeEstimatePersistence.claim(
                offerOne.getId(), routeNow, routeNow.plusSeconds(15)
        );
        assertThat(oldGeneration.originLatitude()).isEqualByComparingTo("37.7000000");

        transportLocationService.update(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "timeline-location-key-2",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7100000"),
                        new BigDecimal("127.2100000"),
                        Instant.now().plusSeconds(2)
                )
        );
        routeEstimatePersistence.complete(
                offerOne.getId(), oldGeneration.generation(), new RouteEstimate(9999, 999), routeNow
        );
        var afterStaleResult = offerRepository.findById(offerOne.getId()).orElseThrow();
        assertThat(afterStaleResult.getRouteEstimateStatus()).isEqualTo(RouteEstimateStatus.CALCULATING);
        assertThat(afterStaleResult.getRouteDistanceMeters()).isNull();

        var currentGeneration = routeEstimatePersistence.claim(
                offerOne.getId(), routeNow.plusSeconds(1), routeNow.plusSeconds(16)
        );
        assertThat(currentGeneration.generation()).isGreaterThan(oldGeneration.generation());
        assertThat(currentGeneration.originLatitude()).isEqualByComparingTo("37.7100000");
        routeEstimatePersistence.complete(
                offerOne.getId(), currentGeneration.generation(), new RouteEstimate(1200, 180),
                routeNow.plusSeconds(2)
        );
        var available = offerRepository.findById(offerOne.getId()).orElseThrow();
        assertThat(available.getRouteEstimateStatus()).isEqualTo(RouteEstimateStatus.AVAILABLE);
        assertThat(available.getLastSuccessRouteDistanceMeters()).isEqualTo(1200);

        transportLocationService.update(
                new AuthenticatedAccount(
                        paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
                ),
                requestId,
                "timeline-location-key-3",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7200000"),
                        new BigDecimal("127.2200000"),
                        Instant.now().plusSeconds(3)
                )
        );
        long failedGeneration = offerRepository.findById(offerOne.getId()).orElseThrow()
                .getRouteEstimateGeneration();
        routeEstimatePersistence.finishUnavailable(offerOne.getId(), failedGeneration, routeNow.plusSeconds(3));
        var unavailable = offerRepository.findById(offerOne.getId()).orElseThrow();
        assertThat(unavailable.getRouteEstimateStatus()).isEqualTo(RouteEstimateStatus.UNAVAILABLE);
        assertThat(unavailable.getRouteDistanceMeters()).isNull();
        assertThat(unavailable.getLastSuccessRouteDistanceMeters()).isEqualTo(1200);
        assertThat(auditEventRepository.countByAction(AuditAction.AMBULANCE_LOCATION_UPDATED)).isEqualTo(3);
        assertThat(outboxEventRepository.countByEventType(RealtimeEventType.AMBULANCE_LOCATION_UPDATED))
                .isEqualTo(5);
    }

    @Test
    void inFlightDynamicEtaIsDiscardedAfterCurrentDestinationWithdrawal() {
        UserAccount paramedic = createParamedic("withdrawaletamedic");
        UserAccount hospital = createHospital("withdrawaletahospital", "37.6021000");
        String requestId = createAndSearch(paramedic, "withdrawal-eta-request");
        var offer = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId).stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getPublicId()
                        .equals(hospital.getPublicId()))
                .findFirst()
                .orElseThrow();
        AuthenticatedAccount hospitalPrincipal = new AuthenticatedAccount(
                hospital.getPublicId(), hospital.getOrganization().getPublicId(), UserRole.HOSPITAL_STAFF
        );
        AuthenticatedAccount paramedicPrincipal = new AuthenticatedAccount(
                paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
        );
        hospitalOfferService.accept(hospitalPrincipal, offer.getPublicId(), "withdrawal-eta-accept");
        destinationService.select(
                paramedicPrincipal, requestId, "withdrawal-eta-destination", offer.getPublicId()
        );
        transportLocationService.update(
                paramedicPrincipal,
                requestId,
                "withdrawal-eta-location",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7100000"),
                        new BigDecimal("127.2100000"),
                        Instant.parse("2026-08-04T10:30:00Z")
                )
        );

        Instant routeNow = Instant.now().plusSeconds(1);
        var inFlight = routeEstimatePersistence.claim(offer.getId(), routeNow, routeNow.plusSeconds(15));
        assertThat(inFlight).isNotNull();
        assertThat(inFlight.generation()).isGreaterThan(0);

        hospitalOfferService.withdrawAcceptance(
                hospitalPrincipal,
                offer.getPublicId(),
                "withdrawal-eta-command",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        routeEstimatePersistence.complete(
                offer.getId(), inFlight.generation(), new RouteEstimate(9999, 999), routeNow.plusSeconds(2)
        );

        var afterLateResult = offerRepository.findById(offer.getId()).orElseThrow();
        assertThat(afterLateResult.getStatus()).isEqualTo(HospitalOfferStatus.ACCEPTANCE_WITHDRAWN);
        assertThat(afterLateResult.getRouteDistanceMeters()).isNull();
        assertThat(afterLateResult.getEtaSeconds()).isNull();

        assertThat(routeEstimatePersistence.claim(
                offer.getId(), routeNow.plusSeconds(16), routeNow.plusSeconds(31)
        )).isNull();
        assertThat(offerRepository.findById(offer.getId()).orElseThrow().getRouteEstimateStatus())
                .isEqualTo(RouteEstimateStatus.UNAVAILABLE);
    }

    @Test
    void reRequestedPendingOfferUsesRecoverySnapshotAndOriginUntilSelected() throws Exception {
        UserAccount paramedic = createParamedic("renotifysnapshotmedic");
        UserAccount destinationHospital = createHospital("renotifysnapshotdestination", "37.6021000");
        UserAccount pendingHospital = createHospital("renotifysnapshotpending", "37.6121000");
        String requestId = createAndSearch(paramedic, "renotify-snapshot-request");
        var offers = offerRepository.findByTransportRequestPublicIdOrderByOfferedAtAsc(requestId);
        var destinationOffer = offers.stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId()
                        .equals(destinationHospital.getId()))
                .findFirst()
                .orElseThrow();
        var pendingOffer = offers.stream()
                .filter(candidate -> candidate.getHospitalProfile().getAccount().getId()
                        .equals(pendingHospital.getId()))
                .findFirst()
                .orElseThrow();
        AuthenticatedAccount paramedicPrincipal = new AuthenticatedAccount(
                paramedic.getPublicId(), paramedic.getOrganization().getPublicId(), UserRole.PARAMEDIC
        );
        AuthenticatedAccount destinationPrincipal = new AuthenticatedAccount(
                destinationHospital.getPublicId(), destinationHospital.getOrganization().getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
        AuthenticatedAccount pendingPrincipal = new AuthenticatedAccount(
                pendingHospital.getPublicId(), pendingHospital.getOrganization().getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
        hospitalOfferService.accept(
                destinationPrincipal, destinationOffer.getPublicId(), "renotify-snapshot-accept-a"
        );
        destinationService.select(
                paramedicPrincipal, requestId, "renotify-snapshot-select-a", destinationOffer.getPublicId()
        );

        var initialVitals = ValidTransportRequestFixtures.request().vitalSigns();
        var visibleAtRenotification = new UpdateVitalSignsRequest(
                initialVitals.measuredAt().plusSeconds(60),
                initialVitals.enteredAt().plusSeconds(60),
                initialVitals.measurements().stream().map(measurement ->
                        new UpdateVitalSignsRequest.VitalSignInput(
                                measurement.type(), measurement.state(), measurement.primaryValue(),
                                measurement.secondaryValue(), measurement.unavailableReason(),
                                measurement.unavailableDetail()
                        )).toList()
        );
        clinicalUpdateService.addVitalSigns(
                paramedicPrincipal,
                requestId,
                "renotify-snapshot-visible-update",
                visibleAtRenotification
        );
        transportLocationService.update(
                paramedicPrincipal,
                requestId,
                "renotify-snapshot-location",
                new UpdateTransportLocationRequest(
                        new BigDecimal("37.7100000"),
                        new BigDecimal("127.2100000"),
                        Instant.parse("2026-08-04T11:00:00Z")
                )
        );

        hospitalOfferService.withdrawAcceptance(
                destinationPrincipal,
                destinationOffer.getPublicId(),
                "renotify-snapshot-withdraw-a",
                new WithdrawHospitalAcceptanceRequest(HospitalAcceptanceWithdrawalReason.BED_SHORTAGE, null)
        );
        var recovery = attemptRepository
                .findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(requestId)
                .orElseThrow();
        var reRequested = offerRepository.findById(pendingOffer.getId()).orElseThrow();
        assertThat(reRequested.isReRequested()).isTrue();
        assertThat(reRequested.getLastRequestedAttempt().getId()).isEqualTo(recovery.getId());
        assertThat(recovery.getSearchOriginLatitude()).isEqualByComparingTo("37.7100000");
        assertThat(recovery.getSearchOriginLongitude()).isEqualByComparingTo("127.2100000");

        var hiddenAfterRenotification = new UpdateVitalSignsRequest(
                initialVitals.measuredAt().plusSeconds(120),
                initialVitals.enteredAt().plusSeconds(120),
                initialVitals.measurements().stream().map(measurement ->
                        new UpdateVitalSignsRequest.VitalSignInput(
                                measurement.type(), measurement.state(), measurement.primaryValue(),
                                measurement.secondaryValue(), measurement.unavailableReason(),
                                measurement.unavailableDetail()
                        )).toList()
        );
        clinicalUpdateService.addVitalSigns(
                paramedicPrincipal,
                requestId,
                "renotify-snapshot-hidden-update",
                hiddenAfterRenotification
        );

        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", pendingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timing.reRequested").value(true))
                .andExpect(jsonPath("$.vitalSigns.measuredAt")
                        .value(visibleAtRenotification.measuredAt().toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", pendingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSPORT_005"));

        Instant routeNow = Instant.now().plusSeconds(1);
        var inFlight = routeEstimatePersistence.claim(
                pendingOffer.getId(), routeNow, routeNow.plusSeconds(15)
        );
        assertThat(inFlight).isNotNull();
        assertThat(inFlight.originLatitude()).isEqualByComparingTo("37.7100000");
        assertThat(inFlight.originLongitude()).isEqualByComparingTo("127.2100000");
        hospitalOfferService.accept(
                pendingPrincipal, pendingOffer.getPublicId(), "renotify-snapshot-accept-c"
        );
        routeEstimatePersistence.complete(
                pendingOffer.getId(), inFlight.generation(), new RouteEstimate(1400, 210),
                routeNow.plusSeconds(1)
        );
        assertThat(offerRepository.findById(pendingOffer.getId()).orElseThrow().getRouteEstimateStatus())
                .isEqualTo(RouteEstimateStatus.AVAILABLE);

        destinationService.select(
                paramedicPrincipal, requestId, "renotify-snapshot-select-c", pendingOffer.getPublicId()
        );
        var selectedOffer = offerRepository.findById(pendingOffer.getId()).orElseThrow();
        assertThat(selectedOffer.getRouteEstimateGeneration()).isGreaterThan(inFlight.generation());
        assertThat(selectedOffer.getRouteEstimateStatus()).isEqualTo(RouteEstimateStatus.CALCULATING);
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}", pendingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vitalSigns.measuredAt")
                        .value(hiddenAfterRenotification.measuredAt().toString()));
        mockMvc.perform(get("/api/v1/hospitals/me/offers/{offerId}/location", pendingOffer.getPublicId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(pendingHospital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latitude").value(37.71));
    }

    private String createAndSearch(UserAccount paramedic, String idempotencyKey) {
        return createAndSearch(paramedic, idempotencyKey, ValidTransportRequestFixtures.request());
    }

    private String createAndSearch(
            UserAccount paramedic,
            String idempotencyKey,
            com.hansungteam.ersync.transport.api.CreateTransportRequestRequest request
    ) {
        var creation = transportRequestService.create(
                new AuthenticatedAccount(
                        paramedic.getPublicId(),
                        paramedic.getOrganization().getPublicId(),
                        UserRole.PARAMEDIC
                ),
                idempotencyKey,
                request
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
        return createHospital(loginId, "서울특별시 테스트 주소", null, latitude);
    }

    private UserAccount createHospital(
            String loginId,
            String address,
            String detailAddress,
            String latitude
    ) {
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
                address,
                detailAddress,
                new BigDecimal(latitude),
                new BigDecimal("127.0105000"),
                "02-0000-0000"
        );
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private AuthenticatedAccount hospitalPrincipal(UserAccount hospital) {
        return new AuthenticatedAccount(
                hospital.getPublicId(),
                hospital.getOrganization().getPublicId(),
                UserRole.HOSPITAL_STAFF
        );
    }

    private TransportHospitalSearchResponse readHospitalSearch(
            UserAccount paramedic,
            String requestId
    ) throws Exception {
        String body = mockMvc.perform(get("/api/v1/transport-requests/{requestId}/hospital-search", requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(paramedic)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readValue(body, TransportHospitalSearchResponse.class);
    }

    private TransportHospitalSearchResponse.Offer findOffer(
            TransportHospitalSearchResponse response,
            String offerId
    ) {
        return response.offers().stream()
                .filter(offer -> offer.offerId().equals(offerId))
                .findFirst()
                .orElseThrow();
    }

    private void assertHospitalLocationHidden(TransportHospitalSearchResponse.Offer offer) {
        assertThat(offer.hospitalAddress()).isNull();
        assertThat(offer.hospitalDetailAddress()).isNull();
        assertThat(offer.hospitalLatitude()).isNull();
        assertThat(offer.hospitalLongitude()).isNull();
    }

    private void assertHospitalLocation(
            TransportHospitalSearchResponse.Offer offer,
            String address,
            String detailAddress,
            String latitude
    ) {
        assertThat(offer.hospitalAddress()).isEqualTo(address);
        assertThat(offer.hospitalDetailAddress()).isEqualTo(detailAddress);
        assertThat(offer.hospitalLatitude()).isEqualByComparingTo(latitude);
        assertThat(offer.hospitalLongitude()).isEqualByComparingTo("127.0105000");
    }

    private String bearer(UserAccount account) {
        return "Bearer " + jwtTokenService.issue(account).value();
    }
}
