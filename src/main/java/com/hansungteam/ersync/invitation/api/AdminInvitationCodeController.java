package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.invitation.application.InvitationCodeService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 슈퍼 관리자의 가입 코드 관리 API입니다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminInvitationCodeController {

    private final InvitationCodeService invitationCodeService;
    private final Clock clock;

    public AdminInvitationCodeController(InvitationCodeService invitationCodeService, Clock clock) {
        this.invitationCodeService = invitationCodeService;
        this.clock = clock;
    }

    /**
     * 지정한 조직과 역할에 묶인 일회용 가입 코드를 발급합니다.
     *
     * @param organizationId 가입 대상 조직
     * @param request 가입 대상 역할과 만료 조건
     * @param account 인증된 슈퍼 관리자
     * @return 발급된 가입 코드와 원문 코드
     */
    @PostMapping("/organizations/{organizationId}/invitation-codes")
    public IssuedInvitationCodeResponse issue(
            @PathVariable String organizationId,
            @Valid @RequestBody IssueInvitationCodeRequest request,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        Instant expiresAt = request.expiresAt() == null
                ? clock.instant().plus(defaultExpiresInDays(request), ChronoUnit.DAYS)
                : request.expiresAt();
        return IssuedInvitationCodeResponse.from(invitationCodeService.issue(
                account,
                organizationId,
                request.targetRole(),
                expiresAt
        ));
    }

    /**
     * 가입 코드 목록을 원문 없이 조회합니다.
     *
     * @return 가입 코드 목록
     */
    @GetMapping("/invitation-codes")
    public List<InvitationCodeResponse> findAll() {
        return invitationCodeService.findAll().stream()
                .map(InvitationCodeResponse::from)
                .toList();
    }

    /**
     * 아직 사용되지 않은 가입 코드를 폐기합니다.
     *
     * @param invitationCodeId 폐기할 가입 코드 ID
     * @param account 인증된 슈퍼 관리자
     * @return 폐기된 가입 코드 상태
     */
    @PostMapping("/invitation-codes/{invitationCodeId}/revoke")
    public InvitationCodeResponse revoke(
            @PathVariable String invitationCodeId,
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return InvitationCodeResponse.from(invitationCodeService.revoke(account, invitationCodeId));
    }

    private int defaultExpiresInDays(IssueInvitationCodeRequest request) {
        return request.expiresInDays() == null ? 3 : request.expiresInDays();
    }
}
