package com.hansungteam.ersync.transport.domain;

/** 최신 위치 수신 여부와 30초 경계의 조회용 파생 상태입니다. */
public enum LocationFreshness {
    NOT_RECEIVED,
    CURRENT,
    STALE
}
