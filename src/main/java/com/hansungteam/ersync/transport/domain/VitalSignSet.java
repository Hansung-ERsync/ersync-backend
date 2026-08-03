package com.hansungteam.ersync.transport.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 같은 측정 시각에 기록한 다섯 활력징후 묶음입니다. */
@Entity
@Table(name = "vital_sign_sets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VitalSignSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Column(name = "measured_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant measuredAt;

    @Column(name = "entered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enteredAt;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private UserAccount createdBy;

    @OneToMany(mappedBy = "vitalSignSet", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VitalSignMeasurement> measurements = new ArrayList<>();

    private VitalSignSet(
            TransportRequest transportRequest,
            Instant measuredAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.measuredAt = measuredAt;
        this.enteredAt = enteredAt;
        this.serverReceivedAt = serverReceivedAt;
        this.createdBy = createdBy;
    }

    public static VitalSignSet create(
            TransportRequest transportRequest,
            Instant measuredAt,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        return new VitalSignSet(
                transportRequest,
                measuredAt,
                enteredAt,
                serverReceivedAt,
                createdBy
        );
    }

    /** 검증이 끝난 활력징후 한 항목을 이 세트에 추가합니다. */
    public void addMeasurement(
            VitalSignType type,
            VitalSignState state,
            java.math.BigDecimal primaryValue,
            java.math.BigDecimal secondaryValue,
            VitalSignUnavailableReason unavailableReason,
            String unavailableDetail
    ) {
        measurements.add(VitalSignMeasurement.create(
                this,
                type,
                state,
                primaryValue,
                secondaryValue,
                unavailableReason,
                unavailableDetail
        ));
    }
}
