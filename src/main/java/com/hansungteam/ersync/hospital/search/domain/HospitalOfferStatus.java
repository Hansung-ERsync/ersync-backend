package com.hansungteam.ersync.hospital.search.domain;

/** 병원 한 곳에 전달된 이송 요청의 현재 응답 상태입니다. */
public enum HospitalOfferStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    NO_RESPONSE
}
