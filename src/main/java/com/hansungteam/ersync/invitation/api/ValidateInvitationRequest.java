package com.hansungteam.ersync.invitation.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원가입 1단계에서 소속과 역할을 확인할 가입 코드입니다. */
public record ValidateInvitationRequest(
        @NotBlank @Size(max = 200) String invitationCode
) {
}
