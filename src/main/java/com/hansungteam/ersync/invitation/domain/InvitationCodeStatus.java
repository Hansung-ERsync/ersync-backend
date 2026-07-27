package com.hansungteam.ersync.invitation.domain;

/**
 * 가입 코드의 현재 사용 가능 상태입니다.
 */
public enum InvitationCodeStatus {
    AVAILABLE,
    USED,
    EXPIRED,
    REVOKED
}
