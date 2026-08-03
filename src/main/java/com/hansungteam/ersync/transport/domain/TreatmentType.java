package com.hansungteam.ersync.transport.domain;

/** 최초 요청에 기록할 수 있는 처치 종류입니다. */
public enum TreatmentType {
    NONE,
    OXYGEN,
    AIRWAY,
    CPR,
    DEFIBRILLATION_AED,
    IV_FLUID,
    MEDICATION,
    BLEEDING_WOUND,
    IMMOBILIZATION,
    ECG,
    WARMING_COOLING,
    DELIVERY,
    OTHER
}
