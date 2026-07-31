package com.hansungteam.ersync.audit.domain;

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

/** 민감정보 없이 주요 상태 변경의 주체와 대상을 기록합니다. */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private AuditAction action;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_account_id")
    private UserAccount actorAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_organization_id")
    private Organization actorOrganization;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_public_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String targetPublicId;

    @Column(name = "occurred_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant occurredAt;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    private AuditEvent(
            AuditAction action,
            UserAccount actorAccount,
            Organization actorOrganization,
            String targetType,
            String targetPublicId,
            Instant occurredAt,
            String traceId
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.action = action;
        this.actorAccount = actorAccount;
        this.actorOrganization = actorOrganization;
        this.targetType = targetType;
        this.targetPublicId = targetPublicId;
        this.occurredAt = occurredAt;
        this.traceId = traceId;
    }

    /** 안전한 식별자만 포함한 감사 이벤트를 생성합니다. */
    public static AuditEvent record(
            AuditAction action,
            UserAccount actorAccount,
            Organization actorOrganization,
            String targetType,
            String targetPublicId,
            Instant occurredAt,
            String traceId
    ) {
        return new AuditEvent(
                action,
                actorAccount,
                actorOrganization,
                targetType,
                targetPublicId,
                occurredAt,
                traceId
        );
    }

    @PrePersist
    private void ensureDefaults() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}
