package com.hansungteam.ersync.hospital.search.domain;

/** 병원이 이미 한 수락을 철회하는 사유입니다. */
public enum HospitalAcceptanceWithdrawalReason {
    BED_SHORTAGE,
    OPERATING_ROOM_SHORTAGE,
    SPECIALIST_UNAVAILABLE,
    EQUIPMENT_UNAVAILABLE,
    OTHER
}
