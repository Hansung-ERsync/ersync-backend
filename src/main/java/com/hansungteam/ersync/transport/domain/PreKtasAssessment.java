package com.hansungteam.ersync.transport.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
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

import java.time.Instant;
import java.util.UUID;

/** append-only Pre-KTAS 완료 또는 긴급 미완료 기록입니다. */
@Entity
@Table(name = "pre_ktas_assessments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PreKtasAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "classification_status", nullable = false, length = 30)
    private PreKtasClassificationStatus classificationStatus;

    @Column
    private Integer level;

    @Enumerated(EnumType.STRING)
    @Column(name = "exception_reason", length = 40)
    private PreKtasExceptionReason exceptionReason;

    @Column(name = "exception_detail", length = 200)
    private String exceptionDetail;

    @Column(name = "assessed_at", columnDefinition = "datetime(6)")
    private Instant assessedAt;

    @Column(name = "standard_version", nullable = false, length = 50)
    private String standardVersion;

    @Column(name = "entered_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant enteredAt;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private UserAccount createdBy;

    private PreKtasAssessment(
            TransportRequest transportRequest,
            PreKtasClassificationStatus classificationStatus,
            Integer level,
            PreKtasExceptionReason exceptionReason,
            String exceptionDetail,
            Instant assessedAt,
            String standardVersion,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.classificationStatus = classificationStatus;
        this.level = level;
        this.exceptionReason = exceptionReason;
        this.exceptionDetail = exceptionDetail;
        this.assessedAt = assessedAt;
        this.standardVersion = standardVersion;
        this.enteredAt = enteredAt;
        this.serverReceivedAt = serverReceivedAt;
        this.createdBy = createdBy;
    }

    public static PreKtasAssessment create(
            TransportRequest transportRequest,
            PreKtasClassificationStatus classificationStatus,
            Integer level,
            PreKtasExceptionReason exceptionReason,
            String exceptionDetail,
            Instant assessedAt,
            String standardVersion,
            Instant enteredAt,
            Instant serverReceivedAt,
            UserAccount createdBy
    ) {
        return new PreKtasAssessment(
                transportRequest,
                classificationStatus,
                level,
                exceptionReason,
                exceptionDetail,
                assessedAt,
                standardVersion,
                enteredAt,
                serverReceivedAt,
                createdBy
        );
    }
}
