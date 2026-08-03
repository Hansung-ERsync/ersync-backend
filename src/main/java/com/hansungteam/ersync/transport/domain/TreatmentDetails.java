package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** 처치 종류별 세부정보를 자유 JSON 없이 typed 열로 저장하는 값 객체입니다. */
@Embeddable
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TreatmentDetails {

    @Column(name = "treatment_method", length = 100)
    private String method;

    @Column(length = 100)
    private String device;

    @Column(name = "flow_rate_lpm", precision = 8, scale = 2)
    private BigDecimal flowRateLpm;

    @Column(name = "started_at", columnDefinition = "datetime(6)")
    private Instant startedAt;

    @Column
    private Boolean success;

    @Column(name = "current_status", length = 50)
    private String currentStatus;

    @Column
    private Boolean rosc;

    @Column(name = "rosc_at", columnDefinition = "datetime(6)")
    private Instant roscAt;

    @Column(name = "shock_count")
    private Integer shockCount;

    @Column(name = "fluid_name", length = 100)
    private String fluidName;

    @Column(name = "amount_ml", precision = 10, scale = 2)
    private BigDecimal amountMl;

    @Column(name = "medication_name", length = 100)
    private String medicationName;

    @Column(length = 50)
    private String dose;

    @Column(length = 50)
    private String route;

    @Column(name = "treatment_site", length = 100)
    private String site;

    @Column(name = "tourniquet_used")
    private Boolean tourniquetUsed;

    @Column(name = "tourniquet_applied_at", columnDefinition = "datetime(6)")
    private Instant tourniquetAppliedAt;

    @Column(name = "lead_type", length = 20)
    private String leadType;

    @Column(length = 200)
    private String findings;

    @Column
    private Boolean transmitted;

    @Column(name = "birth_at", columnDefinition = "datetime(6)")
    private Instant birthAt;

    @Column(length = 300)
    private String detail;
}
