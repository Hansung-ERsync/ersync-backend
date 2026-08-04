package com.hansungteam.ersync.hospital.search.domain;

/** 같은 이송 요청 안에서 수행되는 병원 탐색 회차 상태입니다. */
public enum HospitalDispatchAttemptStatus {
    SEARCHING,
    STOPPED_ON_ACCEPTANCE,
    EXHAUSTED
}
