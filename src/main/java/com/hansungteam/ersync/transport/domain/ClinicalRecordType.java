package com.hansungteam.ersync.transport.domain;

/** 임상 timeline에 노출되는 append-only 원본 종류입니다. */
public enum ClinicalRecordType {
    VITAL_SIGNS,
    CONSCIOUSNESS,
    PRE_KTAS,
    TREATMENT
}
