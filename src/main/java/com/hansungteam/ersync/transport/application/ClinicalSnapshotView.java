package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.IncidentAssessment;
import com.hansungteam.ersync.transport.domain.PatientDemographics;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.SupplementalAssessmentRecord;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.VitalSignSet;

import java.time.Instant;
import java.util.List;

/** 현재 또는 병원별 공개 종료 시점의 임상정보를 같은 응답 매퍼에 제공하는 읽기 모델입니다. */
public record ClinicalSnapshotView(
        PatientDemographics patientDemographics,
        IncidentAssessment incidentAssessment,
        PreKtasAssessment latestPreKtasAssessment,
        ConsciousnessAssessment latestConsciousnessAssessment,
        VitalSignSet latestVitalSignSet,
        List<TreatmentEvent> currentTreatments,
        SupplementalAssessmentRecord latestSupplementalAssessment,
        Instant lastClinicalUpdateAt
) {
}
