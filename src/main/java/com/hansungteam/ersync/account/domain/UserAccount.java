package com.hansungteam.ersync.account.domain;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 로그인 자격정보와 서버 권한 범위를 보관하는 사용자 계정입니다. */
@Entity
@Table(
        name = "user_accounts",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_accounts_login_id_role",
                columnNames = {"login_id", "role"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @Column(name = "login_id", nullable = false, length = 30)
    private String loginId;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(name = "last_login_at", columnDefinition = "datetime(6)")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    private UserAccount(Organization organization, String loginId, String passwordHash, UserRole role) {
        this.publicId = UUID.randomUUID().toString();
        this.organization = organization;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = AccountStatus.ACTIVE;
    }

    /** 조직에 소속되지 않는 슈퍼 관리자 계정을 생성합니다. */
    public static UserAccount createSuperAdmin(String loginId, String passwordHash) {
        return new UserAccount(null, loginId, passwordHash, UserRole.SUPER_ADMIN);
    }

    /** 가입 코드로 확인된 조직 구성원 계정을 생성합니다. */
    public static UserAccount createMember(
            Organization organization,
            String loginId,
            String passwordHash,
            UserRole role
    ) {
        if (role == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Member account cannot have SUPER_ADMIN role");
        }
        return new UserAccount(organization, loginId, passwordHash, role);
    }

    /** 로그인 성공 시각을 갱신합니다. */
    public void recordLogin(Instant loginAt) {
        lastLoginAt = loginAt;
    }

    /** 계정을 비활성화합니다. */
    public void deactivate() {
        status = AccountStatus.INACTIVE;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
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
