package com.hansungteam.ersync.invitation.infrastructure;

import com.hansungteam.ersync.auth.infrastructure.UserAccountEntity;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.domain.InvitationCodeStatus;
import com.hansungteam.ersync.organization.infrastructure.OrganizationEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 원문을 저장하지 않는 일회용 가입 코드 영속 모델입니다.
 */
@Entity
@Table(name = "invitation_codes")
public class InvitationCodeEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationEntity organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole targetRole;

    @Column(nullable = false, length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationCodeStatus status;

    @Column(nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by", nullable = false)
    private UserAccountEntity issuedBy;

    @Column(nullable = false)
    private Instant issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by")
    private UserAccountEntity usedBy;

    private Instant usedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revoked_by")
    private UserAccountEntity revokedBy;

    private Instant revokedAt;

    @Version
    private long version;

    protected InvitationCodeEntity() {
    }

    public InvitationCodeEntity(
            OrganizationEntity organization,
            UserRole targetRole,
            String codeHash,
            Instant expiresAt,
            UserAccountEntity issuedBy,
            Instant now
    ) {
        this.id = UUID.randomUUID().toString();
        this.organization = organization;
        this.targetRole = targetRole;
        this.codeHash = codeHash;
        this.status = InvitationCodeStatus.AVAILABLE;
        this.expiresAt = expiresAt;
        this.issuedBy = issuedBy;
        this.issuedAt = now;
    }

    public boolean availableAt(Instant now) {
        return status == InvitationCodeStatus.AVAILABLE && expiresAt.isAfter(now);
    }

    public void markUsed(UserAccountEntity account, Instant now) {
        this.status = InvitationCodeStatus.USED;
        this.usedBy = account;
        this.usedAt = now;
    }

    public void revoke(UserAccountEntity actor, Instant now) {
        this.status = InvitationCodeStatus.REVOKED;
        this.revokedBy = actor;
        this.revokedAt = now;
    }

    public void expire() {
        this.status = InvitationCodeStatus.EXPIRED;
    }

    public String id() {
        return id;
    }

    public OrganizationEntity organization() {
        return organization;
    }

    public UserRole targetRole() {
        return targetRole;
    }

    public String codeHash() {
        return codeHash;
    }

    public InvitationCodeStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UserAccountEntity issuedBy() {
        return issuedBy;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public UserAccountEntity usedBy() {
        return usedBy;
    }

    public Instant usedAt() {
        return usedAt;
    }

    public UserAccountEntity revokedBy() {
        return revokedBy;
    }

    public Instant revokedAt() {
        return revokedAt;
    }
}
