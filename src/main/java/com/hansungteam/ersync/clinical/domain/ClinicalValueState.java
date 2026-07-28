package com.hansungteam.ersync.clinical.domain;

/**
 * 활력징후 항목의 값, 측정 불가, 환자 거부 상태입니다.
 */
public enum ClinicalValueState {
    VALUE,
    MEASUREMENT_UNAVAILABLE,
    PATIENT_REFUSED
}
