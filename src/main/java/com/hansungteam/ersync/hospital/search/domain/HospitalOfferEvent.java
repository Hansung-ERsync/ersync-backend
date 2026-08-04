package com.hansungteam.ersync.hospital.search.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.organization.domain.Organization;
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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 병원 제안의 전달·응답·무응답 사실을 보존하는 불변 이력입니다. */
@Entity
@Table(name = "hospital_offer_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalOfferEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hospital_offer_id", nullable = false)
    private HospitalOffer hospitalOffer;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private HospitalOfferEventType eventType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_account_id")
    private UserAccount actorAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_organization_id")
    private Organization actorOrganization;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 50)
    private HospitalRejectionReason rejectionReason;

    @Column(name = "rejection_detail", length = 200)
    private String rejectionDetail;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_reason", length = 50)
    private HospitalAcceptanceWithdrawalReason withdrawalReason;

    @Column(name = "withdrawal_detail", length = 200)
    private String withdrawalDetail;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant occurredAt;

    private HospitalOfferEvent(
            HospitalOffer hospitalOffer,
            HospitalOfferEventType eventType,
            UserAccount actorAccount,
            Organization actorOrganization,
            HospitalRejectionReason rejectionReason,
            String rejectionDetail,
            Instant occurredAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.hospitalOffer = hospitalOffer;
        this.eventType = eventType;
        this.actorAccount = actorAccount;
        this.actorOrganization = actorOrganization;
        this.rejectionReason = rejectionReason;
        this.rejectionDetail = rejectionDetail;
        this.occurredAt = occurredAt;
    }

    public static HospitalOfferEvent record(
            HospitalOffer hospitalOffer,
            HospitalOfferEventType eventType,
            UserAccount actorAccount,
            Organization actorOrganization,
            HospitalRejectionReason rejectionReason,
            String rejectionDetail,
            Instant occurredAt
    ) {
        return new HospitalOfferEvent(
                hospitalOffer,
                eventType,
                actorAccount,
                actorOrganization,
                rejectionReason,
                rejectionDetail,
                occurredAt
        );
    }

    public static HospitalOfferEvent recordWithdrawal(
            HospitalOffer hospitalOffer,
            UserAccount actorAccount,
            Organization actorOrganization,
            HospitalAcceptanceWithdrawalReason withdrawalReason,
            String withdrawalDetail,
            Instant occurredAt
    ) {
        HospitalOfferEvent event = new HospitalOfferEvent(
                hospitalOffer,
                HospitalOfferEventType.ACCEPTANCE_WITHDRAWN,
                actorAccount,
                actorOrganization,
                null,
                null,
                occurredAt
        );
        event.withdrawalReason = withdrawalReason;
        event.withdrawalDetail = withdrawalDetail;
        return event;
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
