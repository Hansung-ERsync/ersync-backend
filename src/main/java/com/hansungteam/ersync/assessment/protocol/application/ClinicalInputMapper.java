package com.hansungteam.ersync.assessment.protocol.application;

import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest;
import com.hansungteam.ersync.transport.api.UpdateConsciousnessRequest;
import com.hansungteam.ersync.transport.api.UpdatePreKtasRequest;
import com.hansungteam.ersync.transport.api.UpdateTreatmentRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;

import java.util.List;

/** 공개 API DTO를 임상 공통 검증 입력으로 변환합니다. */
public final class ClinicalInputMapper {

    private ClinicalInputMapper() {
    }

    public static ClinicalInput.PreKtas from(CreateTransportRequestRequest.PreKtasInput input) {
        return new ClinicalInput.PreKtas(
                input.classificationStatus(), input.level(), input.exceptionReason(), input.exceptionDetail(),
                input.assessedAt(), input.standardVersion(), input.enteredAt()
        );
    }

    public static ClinicalInput.PreKtas from(UpdatePreKtasRequest input) {
        return new ClinicalInput.PreKtas(
                input.classificationStatus(), input.level(), input.exceptionReason(), input.exceptionDetail(),
                input.assessedAt(), input.standardVersion(), input.enteredAt()
        );
    }

    public static ClinicalInput.Consciousness from(CreateTransportRequestRequest.ConsciousnessInput input) {
        return new ClinicalInput.Consciousness(
                input.avpu(), input.unassessableReason(), input.unassessableDetail(),
                input.observedAt(), input.enteredAt()
        );
    }

    public static ClinicalInput.Consciousness from(UpdateConsciousnessRequest input) {
        return new ClinicalInput.Consciousness(
                input.avpu(), input.unassessableReason(), input.unassessableDetail(),
                input.observedAt(), input.enteredAt()
        );
    }

    public static ClinicalInput.VitalSigns from(CreateTransportRequestRequest.VitalSignsInput input) {
        return new ClinicalInput.VitalSigns(
                input.measuredAt(), input.enteredAt(), input.measurements().stream().map(measurement ->
                        new ClinicalInput.VitalSign(
                                measurement.type(), measurement.state(), measurement.primaryValue(),
                                measurement.secondaryValue(), measurement.unavailableReason(),
                                measurement.unavailableDetail()
                        )).toList()
        );
    }

    public static ClinicalInput.VitalSigns from(UpdateVitalSignsRequest input) {
        return new ClinicalInput.VitalSigns(
                input.measuredAt(), input.enteredAt(), input.measurements().stream().map(measurement ->
                        new ClinicalInput.VitalSign(
                                measurement.type(), measurement.state(), measurement.primaryValue(),
                                measurement.secondaryValue(), measurement.unavailableReason(),
                                measurement.unavailableDetail()
                        )).toList()
        );
    }

    public static List<ClinicalInput.Treatment> from(List<CreateTransportRequestRequest.TreatmentInput> inputs) {
        return inputs.stream().map(ClinicalInputMapper::from).toList();
    }

    public static ClinicalInput.Treatment from(CreateTransportRequestRequest.TreatmentInput input) {
        return new ClinicalInput.Treatment(
                input.type(), input.attemptResult(), details(input.details()), input.performedAt(), input.enteredAt()
        );
    }

    public static ClinicalInput.Treatment from(UpdateTreatmentRequest input) {
        return new ClinicalInput.Treatment(
                input.type(), input.attemptResult(), details(input.details()), input.performedAt(), input.enteredAt()
        );
    }

    private static ClinicalInput.TreatmentDetails details(CreateTransportRequestRequest.TreatmentDetailsInput input) {
        if (input == null) {
            return null;
        }
        return new ClinicalInput.TreatmentDetails(
                input.method(), input.device(), input.flowRateLpm(), input.startedAt(), input.success(),
                input.currentStatus(), input.rosc(), input.roscAt(), input.shockCount(), input.fluidName(),
                input.amountMl(), input.medicationName(), input.dose(), input.route(), input.site(),
                input.tourniquetUsed(), input.tourniquetAppliedAt(), input.leadType(), input.findings(),
                input.transmitted(), input.birthAt(), input.detail()
        );
    }

    private static ClinicalInput.TreatmentDetails details(UpdateTreatmentRequest.TreatmentDetailsInput input) {
        if (input == null) {
            return null;
        }
        return new ClinicalInput.TreatmentDetails(
                input.method(), input.device(), input.flowRateLpm(), input.startedAt(), input.success(),
                input.currentStatus(), input.rosc(), input.roscAt(), input.shockCount(), input.fluidName(),
                input.amountMl(), input.medicationName(), input.dose(), input.route(), input.site(),
                input.tourniquetUsed(), input.tourniquetAppliedAt(), input.leadType(), input.findings(),
                input.transmitted(), input.birthAt(), input.detail()
        );
    }
}
