package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.util.EnumSet;
import java.util.Set;

/** 구급대원 홈과 이력 화면에서 사용하는 요청 상태 묶음입니다. */
public enum TransportRequestView {
    ACTIVE(EnumSet.of(
            TransportRequestStatus.SEARCHING,
            TransportRequestStatus.CANDIDATES_EXHAUSTED,
            TransportRequestStatus.ACCEPTED_AVAILABLE,
            TransportRequestStatus.EN_ROUTE,
            TransportRequestStatus.HANDOFF_REQUESTED
    )),
    HISTORY(EnumSet.of(
            TransportRequestStatus.COMPLETED,
            TransportRequestStatus.CANCELLED
    )),
    RECENT(EnumSet.of(
            TransportRequestStatus.HANDOFF_REQUESTED,
            TransportRequestStatus.COMPLETED,
            TransportRequestStatus.CANCELLED
    ));

    private final Set<TransportRequestStatus> statuses;

    TransportRequestView(Set<TransportRequestStatus> statuses) {
        this.statuses = Set.copyOf(statuses);
    }

    public Set<TransportRequestStatus> statuses() {
        return statuses;
    }
}
