package com.hansungteam.ersync.profile;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.UpdateHospitalProfileRequest;
import com.hansungteam.ersync.hospital.application.HospitalProfileCommandService;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalDispatchAttemptRepository;
import com.hansungteam.ersync.hospital.search.infrastructure.HospitalOfferRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import com.hansungteam.ersync.paramedic.api.UpdateParamedicProfileRequest;
import com.hansungteam.ersync.paramedic.application.ParamedicProfileCommandService;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.domain.ContactSharingConsent;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.ValidTransportRequestFixtures;
import com.hansungteam.ersync.transport.application.TransportRequestService;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProfileUpdateSnapshotIntegrationTest {

    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private ParamedicProfileRepository paramedicProfileRepository;
    @Autowired private HospitalProfileRepository hospitalProfileRepository;
    @Autowired private ContactSharingConsentRepository consentRepository;
    @Autowired private TransportRequestRepository transportRequestRepository;
    @Autowired private HospitalDispatchAttemptRepository attemptRepository;
    @Autowired private HospitalOfferRepository offerRepository;
    @Autowired private TransportRequestService transportRequestService;
    @Autowired private HospitalSearchService hospitalSearchService;
    @Autowired private ParamedicProfileCommandService paramedicProfileCommandService;
    @Autowired private HospitalProfileCommandService hospitalProfileCommandService;

    @Test
    void existingTransportAndOfferKeepSnapshotsWhileNewOnesUseUpdatedProfiles() {
        UserAccount paramedic = createParamedic();
        UserAccount hospital = createHospital();
        AuthenticatedAccount paramedicAuth = authenticated(paramedic, UserRole.PARAMEDIC);
        AuthenticatedAccount hospitalAuth = authenticated(hospital, UserRole.HOSPITAL_STAFF);

        var firstCreation = transportRequestService.create(
                paramedicAuth,
                "profile-snapshot-request-1",
                ValidTransportRequestFixtures.request()
        );
        HospitalOffer firstOffer = processAndGetOnlyOffer(firstCreation.response().transportRequestId());
        TransportRequest firstRequest = transportRequestRepository
                .findByPublicId(firstCreation.response().transportRequestId())
                .orElseThrow();

        paramedicProfileCommandService.update(
                paramedicAuth,
                new UpdateParamedicProfileRequest("수정된 대원", "010-9999-8888")
        );
        hospitalProfileCommandService.update(
                hospitalAuth,
                new UpdateHospitalProfileRequest(
                        "서울특별시 종로구 새 응급실 주소",
                        "별관 2층 응급실",
                        new BigDecimal("37.6121000"),
                        new BigDecimal("127.0205000"),
                        "02-9999-8888"
                )
        );

        var secondCreation = transportRequestService.create(
                paramedicAuth,
                "profile-snapshot-request-2",
                ValidTransportRequestFixtures.request()
        );
        HospitalOffer secondOffer = processAndGetOnlyOffer(secondCreation.response().transportRequestId());
        TransportRequest secondRequest = transportRequestRepository
                .findByPublicId(secondCreation.response().transportRequestId())
                .orElseThrow();

        assertThat(firstRequest.getCallbackContact()).isEqualTo("010-0000-0001");
        assertThat(secondRequest.getCallbackContact()).isEqualTo("010-9999-8888");

        assertThat(firstOffer.getHospitalAddressSnapshot()).isEqualTo("서울특별시 성북구 기존 주소");
        assertThat(firstOffer.getHospitalDetailAddressSnapshot()).isEqualTo("본관 1층 응급실");
        assertThat(firstOffer.getHospitalLatitudeSnapshot()).isEqualByComparingTo("37.6021000");
        assertThat(firstOffer.getHospitalLongitudeSnapshot()).isEqualByComparingTo("127.0105000");
        assertThat(firstOffer.getHospitalContactSnapshot()).isEqualTo("02-1234-5678");

        assertThat(secondOffer.getHospitalAddressSnapshot()).isEqualTo("서울특별시 종로구 새 응급실 주소");
        assertThat(secondOffer.getHospitalDetailAddressSnapshot()).isEqualTo("별관 2층 응급실");
        assertThat(secondOffer.getHospitalLatitudeSnapshot()).isEqualByComparingTo("37.6121000");
        assertThat(secondOffer.getHospitalLongitudeSnapshot()).isEqualByComparingTo("127.0205000");
        assertThat(secondOffer.getHospitalContactSnapshot()).isEqualTo("02-9999-8888");
    }

    private HospitalOffer processAndGetOnlyOffer(String requestId) {
        var attempt = attemptRepository.findByTransportRequestPublicIdAndAttemptNumber(requestId, 1)
                .orElseThrow();
        hospitalSearchService.processDueAttempt(attempt.getId());
        return offerRepository.findByDispatchAttemptIdOrderByOfferedAtAsc(attempt.getId())
                .getFirst();
    }

    private UserAccount createParamedic() {
        Organization organization = organizationRepository.save(Organization.create(
                "프로필 스냅샷 구급대",
                OrganizationType.EMS_UNIT
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "profilesnapshotmedic",
                "encoded-password",
                UserRole.PARAMEDIC
        ));
        paramedicProfileRepository.save(ParamedicProfile.create(
                account,
                organization,
                "기존 대원",
                "010-0000-0001"
        ));
        consentRepository.save(ContactSharingConsent.record(
                account,
                "CONTACT_SHARING_DEV_1.0",
                Instant.parse("2026-08-03T09:00:00Z")
        ));
        return account;
    }

    private UserAccount createHospital() {
        Organization organization = organizationRepository.save(Organization.create(
                "프로필 스냅샷 병원",
                OrganizationType.HOSPITAL
        ));
        UserAccount account = userAccountRepository.save(UserAccount.createMember(
                organization,
                "profilesnapshothospital",
                "encoded-password",
                UserRole.HOSPITAL_STAFF
        ));
        HospitalProfile profile = HospitalProfile.create(
                organization,
                account,
                "서울특별시 성북구 기존 주소",
                "본관 1층 응급실",
                new BigDecimal("37.6021000"),
                new BigDecimal("127.0105000"),
                "02-1234-5678"
        );
        profile.changeReceivingStatus(ReceivingStatus.ON);
        hospitalProfileRepository.save(profile);
        return account;
    }

    private AuthenticatedAccount authenticated(UserAccount account, UserRole role) {
        return new AuthenticatedAccount(
                account.getPublicId(),
                account.getOrganization().getPublicId(),
                role
        );
    }
}
