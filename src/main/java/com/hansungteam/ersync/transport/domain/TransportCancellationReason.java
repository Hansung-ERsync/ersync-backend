package com.hansungteam.ersync.transport.domain;

/** 구급대원이 활성 이송을 종료할 때 선택하는 MVP 취소 사유입니다. */
public enum TransportCancellationReason {
    PATIENT_REFUSED_TRANSPORT,
    GUARDIAN_SELF_TRANSPORT,
    SCENE_RESOLVED,
    OTHER
}
