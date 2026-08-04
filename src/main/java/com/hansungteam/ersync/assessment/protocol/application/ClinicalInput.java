package com.hansungteam.ersync.assessment.protocol.application;

import com.hansungteam.ersync.transport.domain.Avpu;
import com.hansungteam.ersync.transport.domain.ConsciousnessUnassessableReason;
import com.hansungteam.ersync.transport.domain.PreKtasClassificationStatus;
import com.hansungteam.ersync.transport.domain.PreKtasExceptionReason;
import com.hansungteam.ersync.transport.domain.TreatmentAttemptResult;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.domain.VitalSignState;
import com.hansungteam.ersync.transport.domain.VitalSignType;
import com.hansungteam.ersync.transport.domain.VitalSignUnavailableReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 최초 평가와 후속 갱신이 함께 사용하는 임상 입력의 정규화된 내부 표현입니다. */
public final class ClinicalInput {

    private ClinicalInput() {
    }

    public record PreKtas(
            PreKtasClassificationStatus classificationStatus,
            Integer level,
            PreKtasExceptionReason exceptionReason,
            String exceptionDetail,
            Instant assessedAt,
            String standardVersion,
            Instant enteredAt
    ) {
    }

    public record Consciousness(
            Avpu avpu,
            ConsciousnessUnassessableReason unassessableReason,
            String unassessableDetail,
            Instant observedAt,
            Instant enteredAt
    ) {
    }

    public record VitalSigns(
            Instant measuredAt,
            Instant enteredAt,
            List<VitalSign> measurements
    ) {
    }

    public record VitalSign(
            VitalSignType type,
            VitalSignState state,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            VitalSignUnavailableReason unavailableReason,
            String unavailableDetail
    ) {
    }

    public record Treatment(
            TreatmentType type,
            TreatmentAttemptResult attemptResult,
            TreatmentDetails details,
            Instant performedAt,
            Instant enteredAt
    ) {
    }

    public record TreatmentDetails(
            String method,
            String device,
            BigDecimal flowRateLpm,
            Instant startedAt,
            Boolean success,
            String currentStatus,
            Boolean rosc,
            Instant roscAt,
            Integer shockCount,
            String fluidName,
            BigDecimal amountMl,
            String medicationName,
            String dose,
            String route,
            String site,
            Boolean tourniquetUsed,
            Instant tourniquetAppliedAt,
            String leadType,
            String findings,
            Boolean transmitted,
            Instant birthAt,
            String detail
    ) {
    }
}
