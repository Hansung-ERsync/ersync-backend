package com.hansungteam.ersync.invitation.api;

/** 발급 시 한 번만 원문을 포함하는 가입 코드 응답입니다. */
public record IssuedInvitationResponse(
        String code
) {
}
