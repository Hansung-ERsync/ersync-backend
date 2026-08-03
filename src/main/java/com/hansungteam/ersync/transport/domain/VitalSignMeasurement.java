package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** 활력징후의 수치 또는 측정 불가·환자 거부 상태 한 항목입니다. */
@Entity
@Table(name = "vital_sign_measurements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VitalSignMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vital_sign_set_id", nullable = false)
    private VitalSignSet vitalSignSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_type", nullable = false, length = 30)
    private VitalSignType measurementType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private VitalSignState state;

    @Column(name = "primary_value", precision = 10, scale = 3)
    private BigDecimal primaryValue;

    @Column(name = "secondary_value", precision = 10, scale = 3)
    private BigDecimal secondaryValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "unavailable_reason", length = 30)
    private VitalSignUnavailableReason unavailableReason;

    @Column(name = "unavailable_detail", length = 200)
    private String unavailableDetail;

    private VitalSignMeasurement(
            VitalSignSet vitalSignSet,
            VitalSignType measurementType,
            VitalSignState state,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            VitalSignUnavailableReason unavailableReason,
            String unavailableDetail
    ) {
        this.vitalSignSet = vitalSignSet;
        this.measurementType = measurementType;
        this.state = state;
        this.primaryValue = primaryValue;
        this.secondaryValue = secondaryValue;
        this.unavailableReason = unavailableReason;
        this.unavailableDetail = unavailableDetail;
    }

    static VitalSignMeasurement create(
            VitalSignSet vitalSignSet,
            VitalSignType measurementType,
            VitalSignState state,
            BigDecimal primaryValue,
            BigDecimal secondaryValue,
            VitalSignUnavailableReason unavailableReason,
            String unavailableDetail
    ) {
        return new VitalSignMeasurement(
                vitalSignSet,
                measurementType,
                state,
                primaryValue,
                secondaryValue,
                unavailableReason,
                unavailableDetail
        );
    }
}
