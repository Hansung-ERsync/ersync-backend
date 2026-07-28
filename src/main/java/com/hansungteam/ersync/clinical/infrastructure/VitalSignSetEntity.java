package com.hansungteam.ersync.clinical.infrastructure;

import com.hansungteam.ersync.clinical.api.VitalSignSetRequest;
import com.hansungteam.ersync.clinical.domain.ClinicalValueState;
import com.hansungteam.ersync.clinical.domain.MeasurementUnavailableReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 다섯 항목을 모두 포함하는 활력징후 append-only row입니다.
 */
@Entity
@Table(name = "vital_sign_sets")
public class VitalSignSetEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @Column(nullable = false, length = 36)
    private String transportRequestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicalValueState bloodPressureState;

    private Integer systolic;
    private Integer diastolic;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MeasurementUnavailableReason bloodPressureUnavailableReason;

    @Column(length = 120)
    private String bloodPressureOtherDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicalValueState pulseState;

    private Integer pulsePerMinute;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MeasurementUnavailableReason pulseUnavailableReason;

    @Column(length = 120)
    private String pulseOtherDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicalValueState respiratoryRateState;

    private Integer respirationsPerMinute;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MeasurementUnavailableReason respiratoryUnavailableReason;

    @Column(length = 120)
    private String respiratoryOtherDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicalValueState temperatureState;

    @Column(precision = 4, scale = 1)
    private BigDecimal temperatureCelsius;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MeasurementUnavailableReason temperatureUnavailableReason;

    @Column(length = 120)
    private String temperatureOtherDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ClinicalValueState spo2State;

    private Integer spo2Percent;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private MeasurementUnavailableReason spo2UnavailableReason;

    @Column(length = 120)
    private String spo2OtherDetail;

    @Column(nullable = false)
    private Instant measuredAt;

    @Column(nullable = false)
    private Instant enteredAt;

    @Column(nullable = false)
    private Instant serverReceivedAt;

    @Column(nullable = false, length = 36)
    private String createdBy;

    @Column(length = 36)
    private String supersedesVitalSignSetId;

    @Column(length = 255)
    private String correctionReason;

    protected VitalSignSetEntity() {
    }

    public VitalSignSetEntity(
            String transportRequestId,
            String createdBy,
            Instant serverReceivedAt,
            VitalSignSetRequest request
    ) {
        this.id = UUID.randomUUID().toString();
        this.transportRequestId = transportRequestId;
        this.createdBy = createdBy;
        this.serverReceivedAt = serverReceivedAt;
        this.measuredAt = request.measuredAt();
        this.enteredAt = request.enteredAt();
        this.supersedesVitalSignSetId = request.supersedesVitalSignSetId();
        this.correctionReason = request.correctionReason();
        applyBloodPressure(request.bloodPressure());
        applyPulse(request.pulse());
        applyRespiratoryRate(request.respiratoryRate());
        applyTemperature(request.temperature());
        applySpo2(request.spo2());
    }

    private void applyBloodPressure(VitalSignSetRequest.BloodPressureItem item) {
        this.bloodPressureState = item.state();
        this.systolic = item.systolic();
        this.diastolic = item.diastolic();
        this.bloodPressureUnavailableReason = item.unavailableReason();
        this.bloodPressureOtherDetail = item.otherDetail();
    }

    private void applyPulse(VitalSignSetRequest.IntegerVitalItem item) {
        this.pulseState = item.state();
        this.pulsePerMinute = item.value();
        this.pulseUnavailableReason = item.unavailableReason();
        this.pulseOtherDetail = item.otherDetail();
    }

    private void applyRespiratoryRate(VitalSignSetRequest.IntegerVitalItem item) {
        this.respiratoryRateState = item.state();
        this.respirationsPerMinute = item.value();
        this.respiratoryUnavailableReason = item.unavailableReason();
        this.respiratoryOtherDetail = item.otherDetail();
    }

    private void applyTemperature(VitalSignSetRequest.TemperatureItem item) {
        this.temperatureState = item.state();
        this.temperatureCelsius = item.value();
        this.temperatureUnavailableReason = item.unavailableReason();
        this.temperatureOtherDetail = item.otherDetail();
    }

    private void applySpo2(VitalSignSetRequest.IntegerVitalItem item) {
        this.spo2State = item.state();
        this.spo2Percent = item.value();
        this.spo2UnavailableReason = item.unavailableReason();
        this.spo2OtherDetail = item.otherDetail();
    }

    public String id() {
        return id;
    }
}
