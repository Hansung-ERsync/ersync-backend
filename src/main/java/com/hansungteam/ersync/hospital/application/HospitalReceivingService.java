package com.hansungteam.ersync.hospital.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.api.HospitalReceivingStatusResponse;
import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** 인증된 병원 공용 계정의 자기 응급실 수신 상태만 변경합니다. */
@Service
@RequiredArgsConstructor
public class HospitalReceivingService {

    private final UserAccountRepository userAccountRepository;
    private final HospitalProfileRepository hospitalProfileRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public HospitalReceivingStatusResponse change(
            AuthenticatedAccount principal,
            ReceivingStatus receivingStatus
    ) {
        if (principal.role() != UserRole.HOSPITAL_STAFF) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        HospitalProfile profile = hospitalProfileRepository.findByAccountPublicId(account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        if (!profile.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }

        profile.changeReceivingStatus(receivingStatus);
        HospitalProfile saved = hospitalProfileRepository.saveAndFlush(profile);
        auditService.record(
                AuditAction.HOSPITAL_RECEIVING_STATUS_CHANGED,
                account,
                account.getOrganization(),
                "HOSPITAL_PROFILE",
                profile.getPublicId(),
                clock.instant()
        );
        return HospitalReceivingStatusResponse.from(saved);
    }
}
