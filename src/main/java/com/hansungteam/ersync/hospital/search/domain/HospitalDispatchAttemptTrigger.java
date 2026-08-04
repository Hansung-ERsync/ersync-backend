package com.hansungteam.ersync.hospital.search.domain;

/** 병원 탐색 회차가 시작된 원인입니다. */
public enum HospitalDispatchAttemptTrigger {
    INITIAL,
    MANUAL_RETRY,
    ACCEPTANCE_WITHDRAWAL
}
