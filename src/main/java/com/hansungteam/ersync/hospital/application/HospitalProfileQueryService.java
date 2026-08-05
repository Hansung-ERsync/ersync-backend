package com.hansungteam.ersync.hospital.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.HospitalProfileResponse;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증된 병원 공용 계정의 자기 병원 정보와 실제 수신 상태만 조회합니다. */
@Service
@RequiredArgsConstructor
public class HospitalProfileQueryService {

    private final UserAccountRepository userAccountRepository;
    private final HospitalProfileRepository hospitalProfileRepository;

    @Transactional(readOnly = true)
    public HospitalProfileResponse getMine(AuthenticatedAccount authenticated) {
        if (authenticated.role() != UserRole.HOSPITAL_STAFF) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        requireActiveHospitalContext(account, authenticated);

        HospitalProfile profile = hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        if (!profile.getAccount().getPublicId().equals(account.getPublicId())
                || !profile.getOrganization().getPublicId()
                .equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }

        return HospitalProfileResponse.from(account, profile);
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
