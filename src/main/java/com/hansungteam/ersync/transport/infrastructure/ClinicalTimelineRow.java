package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.ClinicalRecordType;

import java.time.Instant;

/** 네 임상 원본 테이블을 안정적으로 정렬한 timeline 색인 한 행입니다. */
public record ClinicalTimelineRow(
        ClinicalRecordType recordType,
        String recordPublicId,
        Instant clinicalAt,
        Instant serverReceivedAt
) {
}
