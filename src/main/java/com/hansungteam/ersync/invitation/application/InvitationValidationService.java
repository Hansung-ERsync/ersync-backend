package com.hansungteam.ersync.invitation.application;

import com.hansungteam.ersync.global.crypto.SecretDigester;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.api.InvitationValidationResponse;
import com.hansungteam.ersync.invitation.api.RequiredConsentResponse;
import com.hansungteam.ersync.invitation.api.ValidateInvitationRequest;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.privacy.application.ContactSharingConsentPolicy;
import com.hansungteam.ersync.privacy.domain.ConsentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/** 가입 코드를 소비하지 않고 회원가입 화면에 필요한 소속 정보를 확인합니다. */
@Service
@RequiredArgsConstructor
public class InvitationValidationService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final InvitationAvailabilityPolicy invitationAvailabilityPolicy;
    private final ContactSharingConsentPolicy contactSharingConsentPolicy;
    private final SecretDigester secretDigester;
    private final Clock clock;

    @Transactional(readOnly = true)
    public InvitationValidationResponse validate(ValidateInvitationRequest request) {
        InvitationCode invitation = invitationCodeRepository.findByCodeDigest(
                        secretDigester.digest(request.invitationCode().trim())
                )
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_CODE_INVALID));
        invitationAvailabilityPolicy.requireAvailable(invitation, clock.instant());
        if (!invitation.getOrganization().isActive()) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }

        return new InvitationValidationResponse(
                invitation.getOrganization().getPublicId(),
                invitation.getOrganization().getName(),
                invitation.getRole(),
                invitation.getExpiresAt(),
                requiredConsents(invitation.getRole())
        );
    }

    private List<RequiredConsentResponse> requiredConsents(UserRole role) {
        if (role == UserRole.PARAMEDIC) {
            return List.of(
                    new RequiredConsentResponse(
                            ConsentType.CONTACT_COLLECTION_USE,
                            contactSharingConsentPolicy.collectionUsePolicyVersion()
                    ),
                    new RequiredConsentResponse(
                            ConsentType.HOSPITAL_PROVISION,
                            contactSharingConsentPolicy.hospitalProvisionPolicyVersion()
                    )
            );
        }
        if (role == UserRole.HOSPITAL_STAFF) {
            return List.of(new RequiredConsentResponse(
                    ConsentType.CONTACT_COLLECTION_AND_PROVISION,
                    contactSharingConsentPolicy.activePolicyVersion()
            ));
        }
        return List.of();
    }
}
