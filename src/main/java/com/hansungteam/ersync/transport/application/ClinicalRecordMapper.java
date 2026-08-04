package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInput;
import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TreatmentDetails;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.VitalSignSet;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 검증된 공통 임상 입력을 기존 append-only Entity로 변환합니다. */
@Component
public class ClinicalRecordMapper {

    public VitalSignSet vitalSigns(
            TransportRequest request,
            UserAccount account,
            ClinicalInput.VitalSigns input,
            Instant receivedAt
    ) {
        VitalSignSet set = VitalSignSet.create(
                request, input.measuredAt(), input.enteredAt(), receivedAt, account
        );
        input.measurements().forEach(measurement -> set.addMeasurement(
                measurement.type(), measurement.state(), measurement.primaryValue(),
                measurement.secondaryValue(), measurement.unavailableReason(),
                trimToNull(measurement.unavailableDetail())
        ));
        return set;
    }

    public ConsciousnessAssessment consciousness(
            TransportRequest request,
            UserAccount account,
            ClinicalInput.Consciousness input,
            Instant receivedAt
    ) {
        return ConsciousnessAssessment.create(
                request, input.avpu(), input.unassessableReason(), trimToNull(input.unassessableDetail()),
                input.observedAt(), input.enteredAt(), receivedAt, account
        );
    }

    public PreKtasAssessment preKtas(
            TransportRequest request,
            UserAccount account,
            ClinicalInput.PreKtas input,
            Instant receivedAt
    ) {
        return PreKtasAssessment.create(
                request, input.classificationStatus(), input.level(), input.exceptionReason(),
                trimToNull(input.exceptionDetail()), input.assessedAt(), input.standardVersion().trim(),
                input.enteredAt(), receivedAt, account
        );
    }

    public TreatmentEvent treatment(
            TransportRequest request,
            UserAccount account,
            ClinicalInput.Treatment input,
            Instant receivedAt
    ) {
        return TreatmentEvent.create(
                request, input.type(), input.attemptResult(), details(input.details()),
                input.performedAt(), input.enteredAt(), receivedAt, account
        );
    }

    private TreatmentDetails details(ClinicalInput.TreatmentDetails details) {
        if (details == null) {
            return null;
        }
        return TreatmentDetails.builder()
                .method(trimToNull(details.method()))
                .device(trimToNull(details.device()))
                .flowRateLpm(details.flowRateLpm())
                .startedAt(details.startedAt())
                .success(details.success())
                .currentStatus(trimToNull(details.currentStatus()))
                .rosc(details.rosc())
                .roscAt(details.roscAt())
                .shockCount(details.shockCount())
                .fluidName(trimToNull(details.fluidName()))
                .amountMl(details.amountMl())
                .medicationName(trimToNull(details.medicationName()))
                .dose(trimToNull(details.dose()))
                .route(trimToNull(details.route()))
                .site(trimToNull(details.site()))
                .tourniquetUsed(details.tourniquetUsed())
                .tourniquetAppliedAt(details.tourniquetAppliedAt())
                .leadType(trimToNull(details.leadType()))
                .findings(trimToNull(details.findings()))
                .transmitted(details.transmitted())
                .birthAt(details.birthAt())
                .detail(trimToNull(details.detail()))
                .build();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
