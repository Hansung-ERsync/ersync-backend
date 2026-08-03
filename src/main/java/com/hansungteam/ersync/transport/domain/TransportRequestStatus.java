package com.hansungteam.ersync.transport.domain;

/** 이송 요청의 서버 상태입니다. */
public enum TransportRequestStatus {
    SEARCHING,
    CANDIDATES_EXHAUSTED,
    ACCEPTED_AVAILABLE,
    EN_ROUTE,
    HANDOFF_REQUESTED,
    COMPLETED,
    CANCELLED
}
