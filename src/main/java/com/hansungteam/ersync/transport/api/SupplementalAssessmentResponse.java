package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.PupilResponse;

import java.time.Instant;

/** 앱 복구와 병원 수용 판단이 공유하는 구조화된 추가 환자 평가입니다. */
public record SupplementalAssessmentResponse(
        Instant assessedAt,
        Instant enteredAt,
        Instant serverReceivedAt,
        Integer glucoseMgDl,
        PupilResponse leftPupil,
        PupilResponse rightPupil,
        String medicalHistory,
        String allergies,
        String medications,
        Boolean isolationConcern
) {
}
