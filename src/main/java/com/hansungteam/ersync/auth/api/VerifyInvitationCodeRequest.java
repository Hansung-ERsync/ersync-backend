package com.hansungteam.ersync.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 가입 전 코드 확인 요청 DTO입니다.
 */
public record VerifyInvitationCodeRequest(
        @NotBlank
        @Size(max = 120)
        String invitationCode
) {
}
