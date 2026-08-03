package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;

import java.time.Instant;

/** 생성 결과에서 민감한 임상정보·좌표·연락처를 제외한 안전한 응답입니다. */
public record CreateTransportRequestResponse(
        String transportRequestId,
        TransportRequestStatus status,
        String assessmentProtocolVersion,
        Instant createdAt
) {

    public static CreateTransportRequestResponse from(TransportRequest request) {
        return new CreateTransportRequestResponse(
                request.getPublicId(),
                request.getStatus(),
                request.getAssessmentProtocolVersion(),
                request.getCreatedAt()
        );
    }
}
