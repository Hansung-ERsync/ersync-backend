package com.hansungteam.ersync.clinical.api;

import com.hansungteam.ersync.clinical.domain.AgeStatus;
import com.hansungteam.ersync.clinical.domain.InjuryMechanism;
import com.hansungteam.ersync.clinical.domain.InjurySite;
import com.hansungteam.ersync.clinical.domain.OccurrenceType;
import com.hansungteam.ersync.clinical.domain.Sex;
import com.hansungteam.ersync.clinical.domain.Symptom;
import com.hansungteam.ersync.clinical.domain.TimeStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;

/**
 * 환자 기본 정보, 발생 정보, 증상과 발생 시각 입력 DTO입니다.
 */
public record PatientAssessmentRequest(
        @NotNull AgeStatus ageStatus,
        Integer ageYears,
        @NotNull Sex sex,
        @NotNull OccurrenceType occurrenceType,
        @Size(max = 120) String occurrenceOtherDetail,
        InjuryMechanism mechanism,
        @Size(max = 120) String mechanismOtherDetail,
        Set<InjurySite> injurySites,
        @NotNull Symptom primarySymptom,
        @Size(max = 120) String primarySymptomOtherDetail,
        Set<Symptom> secondarySymptoms,
        @NotNull TimeStatus onsetTimeStatus,
        @PastOrPresent Instant onsetAt,
        TimeStatus lastKnownWellStatus,
        @PastOrPresent Instant lastKnownWellAt,
        TimeStatus accidentTimeStatus,
        @PastOrPresent Instant accidentAt,
        TimeStatus cardiacArrestTimeStatus,
        @PastOrPresent Instant cardiacArrestAt,
        @NotNull Instant enteredAt,
        String supersedesAssessmentId,
        @Size(max = 255) String correctionReason
) {
}
