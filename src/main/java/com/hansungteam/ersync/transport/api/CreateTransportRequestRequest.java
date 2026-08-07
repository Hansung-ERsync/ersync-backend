package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.transport.domain.AgeStatus;
import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.ConsciousnessUnassessableReason;
import com.hansungteam.ersync.transport.domain.InjuryMechanism;
import com.hansungteam.ersync.transport.domain.InjurySite;
import com.hansungteam.ersync.transport.domain.OccurrenceType;
import com.hansungteam.ersync.transport.domain.OnsetTimeStatus;
import com.hansungteam.ersync.transport.domain.OriginSource;
import com.hansungteam.ersync.transport.domain.PatientSex;
import com.hansungteam.ersync.transport.domain.PupilResponse;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.transport.domain.Symptom;
import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 개발용 최초 환자 평가와 출발 위치를 구조화한 이송 요청 생성 입력입니다. */
public record CreateTransportRequestRequest(
        @NotBlank @Size(max = 50) String assessmentProtocolVersion,
        @NotNull @Valid OriginInput origin,
        @NotNull @Valid PatientInput patient,
        @NotNull @Valid IncidentInput incident,
        @NotNull @Valid PreKtasInput preKtas,
        @NotNull @Valid ConsciousnessInput consciousness,
        @NotNull @Valid VitalSignsInput vitalSigns,
        @NotEmpty @Size(max = 20) List<@NotNull @Valid TreatmentInput> treatments,
        @Valid SupplementalAssessmentInput supplementalAssessment
) {

    /** 이전 앱 요청과 내부 테스트 코드가 선택 필드 추가 전 생성자를 계속 사용할 수 있게 합니다. */
    public CreateTransportRequestRequest(
            String assessmentProtocolVersion,
            OriginInput origin,
            PatientInput patient,
            IncidentInput incident,
            PreKtasInput preKtas,
            ConsciousnessInput consciousness,
            VitalSignsInput vitalSigns,
            List<TreatmentInput> treatments
    ) {
        this(
                assessmentProtocolVersion,
                origin,
                patient,
                incident,
                preKtas,
                consciousness,
                vitalSigns,
                treatments,
                null
        );
    }

    public record OriginInput(
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @NotNull OriginSource source
    ) {
    }

    public record PatientInput(
            @NotNull AgeStatus ageStatus,
            @DecimalMin("0") Integer ageYears,
            @NotNull PatientSex sex
    ) {
    }

    public record IncidentInput(
            @NotNull OccurrenceType occurrenceType,
            InjuryMechanism mechanism,
            @Size(max = 200) String occurrenceDetail,
            Set<@NotNull InjurySite> injurySites,
            @NotNull Symptom primarySymptom,
            @Size(max = 200) String primarySymptomDetail,
            Set<@NotNull Symptom> secondarySymptoms,
            @NotNull OnsetTimeStatus onsetTimeStatus,
            Instant onsetAt,
            @NotNull Instant enteredAt
    ) {
    }

    public record PreKtasInput(
            @NotNull PreKtasClassificationStatus classificationStatus,
            @DecimalMin("1") @DecimalMax("5") Integer level,
            PreKtasExceptionReason exceptionReason,
            @Size(max = 200) String exceptionDetail,
            Instant assessedAt,
            @NotBlank @Size(max = 50) String standardVersion,
            @NotNull Instant enteredAt
    ) {
    }

    public record ConsciousnessInput(
            @NotNull Avpu avpu,
            ConsciousnessUnassessableReason unassessableReason,
            @Size(max = 200) String unassessableDetail,
            @NotNull Instant observedAt,
            @NotNull Instant enteredAt
    ) {
    }

    public record VitalSignsInput(
            @NotNull Instant measuredAt,
            @NotNull Instant enteredAt,
            @NotEmpty @Size(min = 5, max = 5) List<@NotNull @Valid VitalSignInput> measurements
    ) {
    }

    public record VitalSignInput(
            @NotNull VitalSignType type,
            @NotNull VitalSignState state,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            VitalSignUnavailableReason unavailableReason,
            @Size(max = 200) String unavailableDetail
    ) {
    }

    public record TreatmentInput(
            @NotNull TreatmentType type,
            TreatmentAttemptResult attemptResult,
            @Valid TreatmentDetailsInput details,
            Instant performedAt,
            @NotNull Instant enteredAt
    ) {
    }

    public record SupplementalAssessmentInput(
            @NotNull Instant assessedAt,
            @NotNull Instant enteredAt,
            @Min(0) @Max(1000) Integer glucoseMgDl,
            PupilResponse leftPupil,
            PupilResponse rightPupil,
            @Size(max = 120) String medicalHistory,
            @Size(max = 120) String allergies,
            @Size(max = 120) String medications,
            Boolean isolationConcern
    ) {
    }

    public record TreatmentDetailsInput(
            @Size(max = 100) String method,
            @Size(max = 100) String device,
            BigDecimal flowRateLpm,
            Instant startedAt,
            Boolean success,
            @Size(max = 50) String currentStatus,
            Boolean rosc,
            Instant roscAt,
            Integer shockCount,
            @Size(max = 100) String fluidName,
            BigDecimal amountMl,
            @Size(max = 100) String medicationName,
            @Size(max = 50) String dose,
            @Size(max = 50) String route,
            @Size(max = 100) String site,
            Boolean tourniquetUsed,
            Instant tourniquetAppliedAt,
            @Size(max = 20) String leadType,
            @Size(max = 200) String findings,
            Boolean transmitted,
            Instant birthAt,
            @Size(max = 300) String detail
    ) {
    }
}
