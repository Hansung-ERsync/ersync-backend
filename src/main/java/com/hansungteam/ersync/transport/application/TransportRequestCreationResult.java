package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.api.CreateTransportRequestResponse;

/** 새 생성과 동일 요청 재사용을 HTTP 상태로 구분하기 위한 내부 결과입니다. */
public record TransportRequestCreationResult(
        CreateTransportRequestResponse response,
        boolean created
) {
}
