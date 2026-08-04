package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.hospital.search.api.DispatchAttemptResponse;

/** 새 재전송 회차 생성과 동일 명령 재사용을 HTTP 상태로 구분합니다. */
public record DispatchAttemptCreationResult(DispatchAttemptResponse response, boolean created) {
}
