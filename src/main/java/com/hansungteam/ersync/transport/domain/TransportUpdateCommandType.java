package com.hansungteam.ersync.transport.domain;

/** 이송 중 멱등 갱신 명령 종류입니다. */
public enum TransportUpdateCommandType {
    VITAL_SIGNS,
    CONSCIOUSNESS,
    PRE_KTAS,
    TREATMENT,
    LOCATION
}
