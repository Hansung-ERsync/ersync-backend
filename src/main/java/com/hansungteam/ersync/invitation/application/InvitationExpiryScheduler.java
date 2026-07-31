package com.hansungteam.ersync.invitation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 조회되지 않은 가입 코드도 주기적으로 만료 상태로 정리합니다. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "ersync.invitation.expiry-scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InvitationExpiryScheduler {

    private final InvitationService invitationService;

    @Scheduled(fixedDelayString = "${ersync.invitation.expiry-scheduler-delay:60000}")
    public void expireDueCodes() {
        invitationService.expireDueCodes();
    }
}
