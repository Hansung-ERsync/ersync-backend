package com.hansungteam.ersync.invitation.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 사전 확인과 최종 가입이 동일한 가입 코드 상태 오류를 사용하게 합니다. */
@Component
public class InvitationAvailabilityPolicy {

    public void requireAvailable(InvitationCode invitation, Instant now) {
        if (invitation.getStatus() == InvitationStatus.USED) {
            throw new CustomException(ErrorCode.INVITATION_CODE_USED);
        }
        if (invitation.getStatus() == InvitationStatus.REVOKED) {
            throw new CustomException(ErrorCode.INVITATION_CODE_REVOKED);
        }
        if (invitation.getStatus() == InvitationStatus.EXPIRED || invitation.hasExpiredAt(now)) {
            throw new CustomException(ErrorCode.INVITATION_CODE_EXPIRED);
        }
    }
}
