package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.domain.PatientDemographics;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;

import java.time.Instant;

/** 병원 카드가 필요한 최소 임상정보와 공개 가능한 마지막 갱신 시각입니다. */
public record ClinicalSummaryView(
        PatientDemographics patientDemographics,
        PreKtasAssessment latestPreKtasAssessment,
        Instant lastClinicalUpdateAt
) {
}
