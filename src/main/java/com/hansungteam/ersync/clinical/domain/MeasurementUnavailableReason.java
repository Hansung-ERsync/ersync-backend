package com.hansungteam.ersync.clinical.domain;

/**
 * 활력징후 측정 불가 사유입니다.
 */
public enum MeasurementUnavailableReason {
    PATIENT_CONDITION,
    SCENE_DANGER,
    INJURY_SITE,
    DEVICE_ERROR,
    OTHER
}
