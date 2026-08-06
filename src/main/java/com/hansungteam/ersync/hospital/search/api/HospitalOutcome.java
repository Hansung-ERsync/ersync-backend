package com.hansungteam.ersync.hospital.search.api;

/** 병원 웹이 자기 제안의 현재 처리 결과를 표시할 때 사용하는 상태입니다. */
public enum HospitalOutcome {
    AWAITING_RESPONSE,
    ACCEPTED,
    REJECTED,
    NO_RESPONSE,
    ACCEPTANCE_WITHDRAWN,
    NOT_SELECTED,
    HANDOFF_COMPLETED_HERE,
    COMPLETED_ELSEWHERE,
    TRANSPORT_CANCELLED
}
