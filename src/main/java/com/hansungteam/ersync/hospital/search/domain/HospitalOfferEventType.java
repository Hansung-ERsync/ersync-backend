package com.hansungteam.ersync.hospital.search.domain;

/** 덮어쓰지 않고 보존하는 병원 제안 이력 종류입니다. */
public enum HospitalOfferEventType {
    OFFERED,
    RENOTIFIED,
    ACCEPTED,
    REJECTED,
    NO_RESPONSE,
    ACCEPTANCE_WITHDRAWN
}
