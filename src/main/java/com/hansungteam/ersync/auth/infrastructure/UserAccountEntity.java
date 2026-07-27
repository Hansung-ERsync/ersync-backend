package com.hansungteam.ersync.auth.infrastructure;

import com.hansungteam.ersync.global.security.UserRole;
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

import java.time.Instant;
import java.util.UUID;

/**
 * 인증 가능한 사용자 계정의 영속 모델입니다.
 */
@Entity
@Table(name = "user_accounts")
public class UserAccountEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private OrganizationEntity organization;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserRole role;

    @Column(nullable = false, length = 80, unique = true)
    private String loginId;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant lastLoginAt;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(OrganizationEntity organization, UserRole role, String loginId, String passwordHash, Instant now) {
        this.id = UUID.randomUUID().toString();
        this.organization = organization;
        this.role = role;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.active = true;
        this.createdAt = now;
    }

    public void recordLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public String id() {
        return id;
    }

    public OrganizationEntity organization() {
        return organization;
    }

    public UserRole role() {
        return role;
    }

    public String loginId() {
        return loginId;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }
}
