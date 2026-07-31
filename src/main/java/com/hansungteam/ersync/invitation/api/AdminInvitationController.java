package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.invitation.application.InvitationService;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 슈퍼 관리자 전용 가입 코드 관리 API입니다. */
@RestController
@RequestMapping("/api/v1/admin/invitation-codes")
@RequiredArgsConstructor
@Validated
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminInvitationController {

    private final InvitationService invitationService;
    private final CurrentAccountProvider currentAccountProvider;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedInvitationResponse issue(@Valid @RequestBody IssueInvitationRequest request) {
        return invitationService.issue(currentAccountProvider.require().accountId(), request);
    }

    @GetMapping
    public InvitationListResponse list(
            @RequestParam(required = false) InvitationStatus status,
            @RequestParam(required = false) String organizationId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return invitationService.list(status, organizationId, page, size);
    }

    @PostMapping("/{invitationCodeId}/revoke")
    public InvitationResponse revoke(@PathVariable String invitationCodeId) {
        return invitationService.revoke(currentAccountProvider.require().accountId(), invitationCodeId);
    }
}
