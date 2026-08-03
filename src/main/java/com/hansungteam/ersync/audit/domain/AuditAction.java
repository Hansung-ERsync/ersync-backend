package com.hansungteam.ersync.audit.domain;

/** MVP에서 보존해야 하는 주요 감사 행위입니다. */
public enum AuditAction {
    INVITATION_ISSUED,
    INVITATION_USED,
    INVITATION_EXPIRED,
    INVITATION_REVOKED,
    HOSPITAL_RECEIVING_STATUS_CHANGED,
    CONTACT_SHARING_CONSENT_RECORDED,
    TRANSPORT_REQUEST_CREATED
}
