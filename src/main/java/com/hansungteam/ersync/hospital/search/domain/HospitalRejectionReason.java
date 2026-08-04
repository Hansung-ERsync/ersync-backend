package com.hansungteam.ersync.hospital.search.domain;

/** 병원의 최초 요청 거절 사유입니다. */
public enum HospitalRejectionReason {
    ER_GENERAL_BED_SHORTAGE,
    ISOLATION_BED_SHORTAGE,
    OPERATING_ROOM_SHORTAGE,
    ICU_SHORTAGE,
    SPECIALIST_UNAVAILABLE,
    EQUIPMENT_UNAVAILABLE,
    OTHER
}
