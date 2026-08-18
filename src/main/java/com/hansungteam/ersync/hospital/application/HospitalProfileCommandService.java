package com.hansungteam.ersync.hospital.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.HospitalProfileResponse;
import com.hansungteam.ersync.hospital.api.UpdateHospitalProfileRequest;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.privacy.application.ContactPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 인증된 병원 공용 계정의 자기 응급실 위치와 연락처만 변경합니다. */
@Service
@RequiredArgsConstructor
public class HospitalProfileCommandService {

    private final UserAccountRepository userAccountRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public HospitalProfileResponse update(
            AuthenticatedAccount authenticated,
            UpdateHospitalProfileRequest request
    ) {
        if (authenticated.role() != UserRole.HOSPITAL_STAFF) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        String address = HospitalProfilePolicy.normalizeAndValidateAddress(request.address());
        String detailAddress = HospitalProfilePolicy.normalizeOptionalDetailAddress(request.detailAddress());
        String contact = ContactPolicy.normalizeAndValidate(request.contact());

        UserAccount account = userAccountRepository.findByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActiveHospitalContext(account, authenticated);

        HospitalProfile profile = hospitalProfileRepository.findLockedByAccountPublicId(account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        if (!profile.getAccount().getPublicId().equals(account.getPublicId())
                || !profile.getOrganization().getPublicId()
                .equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }

        profile.updateDetails(
                address,
                detailAddress,
                request.latitude(),
                request.longitude(),
                contact
        );
        HospitalProfile saved = hospitalProfileRepository.saveAndFlush(profile);
        auditService.record(
                AuditAction.HOSPITAL_PROFILE_UPDATED,
                account,
                account.getOrganization(),
                "HOSPITAL_PROFILE",
                profile.getPublicId(),
                clock.instant()
        );
        return HospitalProfileResponse.from(account, saved);
    }

    private void requireActiveHospitalContext(
            UserAccount account,
            AuthenticatedAccount authenticated
    ) {
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.HOSPITAL_STAFF) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        if (account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.HOSPITAL
                || authenticated.organizationId() == null
                || !authenticated.organizationId().equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
    }
}
