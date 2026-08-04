package com.hansungteam.ersync.invitation.api;

import com.hansungteam.ersync.invitation.application.InvitationValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원가입 전 가입 코드의 소속과 역할을 확인하는 공개 API입니다. */
@RestController
@RequestMapping("/api/v1/auth/invitations")
@RequiredArgsConstructor
public class PublicInvitationValidationController {

    private final InvitationValidationService invitationValidationService;

    @PostMapping("/validate")
    public InvitationValidationResponse validate(
            @Valid @RequestBody ValidateInvitationRequest request
    ) {
        return invitationValidationService.validate(request);
    }
}
