package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.api.ClinicalUpdateResponse;

/** 신규 임상 원본과 멱등 재사용을 HTTP 상태로 구분합니다. */
public record ClinicalUpdateResult(ClinicalUpdateResponse response, boolean created) {
}
