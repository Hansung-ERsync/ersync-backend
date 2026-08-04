package com.hansungteam.ersync.transport.domain;

/** 이송 종료 과정에서 멱등하게 처리하는 명령 종류입니다. */
public enum TransportLifecycleCommandType {
    CANCEL,
    HANDOFF_REQUEST,
    HANDOFF_CONFIRM
}
