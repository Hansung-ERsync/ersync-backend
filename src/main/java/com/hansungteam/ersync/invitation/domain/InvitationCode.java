package com.hansungteam.ersync.invitation.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.global.security.UserRole;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 조직과 역할에 묶인 일회용 가입 코드입니다. */
@Entity
@Table(name = "invitation_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvitationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Column(name = "code_digest", nullable = false, unique = true, length = 32, columnDefinition = "binary(32)")
    private byte[] codeDigest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvitationStatus status;

    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "used_by_account_id")
    private UserAccount usedByAccount;

    @Column(name = "used_at", columnDefinition = "datetime(6)")
    private Instant usedAt;

    @Column(name = "revoked_at", columnDefinition = "datetime(6)")
    private Instant revokedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_account_id", nullable = false)
    private UserAccount createdByAccount;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private InvitationCode(
            Organization organization,
            UserRole role,
            byte[] codeDigest,
            Instant expiresAt,
            UserAccount createdByAccount
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.organization = organization;
        this.role = role;
        this.codeDigest = codeDigest.clone();
        this.status = InvitationStatus.AVAILABLE;
        this.expiresAt = expiresAt;
        this.createdByAccount = createdByAccount;
    }

    /** 새 가입 코드를 생성합니다. */
    public static InvitationCode issue(
            Organization organization,
            UserRole role,
            byte[] codeDigest,
            Instant expiresAt,
            UserAccount createdByAccount
    ) {
        return new InvitationCode(organization, role, codeDigest, expiresAt, createdByAccount);
    }

    /** 사용 가능한 코드를 계정 가입에 소비합니다. */
    public void use(UserAccount account, Instant usedAt) {
        status = InvitationStatus.USED;
        usedByAccount = account;
        this.usedAt = usedAt;
    }

    /** 사용 전 코드를 운영자가 폐기합니다. */
    public void revoke(Instant revokedAt) {
        status = InvitationStatus.REVOKED;
        this.revokedAt = revokedAt;
    }

    /** 사용 전 코드의 유효기간이 지난 경우 만료 처리합니다. */
    public void expire() {
        status = InvitationStatus.EXPIRED;
    }

    public boolean hasExpiredAt(Instant now) {
        return !expiresAt.isAfter(now);
    }

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
