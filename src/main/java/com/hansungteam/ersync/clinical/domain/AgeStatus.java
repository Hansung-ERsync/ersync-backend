package com.hansungteam.ersync.clinical.domain;

/**
 * 환자 나이가 정확한 값인지, 추정인지, 확인 불가인지 나타냅니다.
 */
public enum AgeStatus {
    EXACT,
    ESTIMATED,
    UNKNOWN
}
