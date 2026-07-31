package com.hansungteam.ersync.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 구급대원 개인 계정 가입 요청입니다. */
public record ParamedicSignupRequest(
        @NotBlank String invitationCode,
        @NotBlank @Pattern(regexp = "[a-z0-9]{4,30}") String loginId,
        @NotBlank @Size(min = 8, max = 64) String password
) {
}
