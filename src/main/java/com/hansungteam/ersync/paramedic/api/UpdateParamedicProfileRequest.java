package com.hansungteam.ersync.paramedic.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 로그인한 구급대원이 자기 표시 이름과 병원 회신 연락처를 전체 수정하는 요청입니다. */
public record UpdateParamedicProfileRequest(
        @NotBlank @Size(max = 50) String displayName,
        @NotBlank @Size(max = 30) String callbackContact
) {
}
