package com.hansungteam.ersync.invitation.domain;

/** 일회용 가입 코드의 현재 상태입니다. */
public enum InvitationStatus {
    AVAILABLE,
    USED,
    EXPIRED,
    REVOKED
}
