package com.hansungteam.ersync.auth.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/** 서버에서 폐기와 일회성 회전을 관리하는 Refresh Token입니다. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @Column(name = "token_digest", nullable = false, unique = true, length = 32, columnDefinition = "binary(32)")
    private byte[] tokenDigest;

    @Column(name = "expires_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant expiresAt;

    @Column(name = "used_at", columnDefinition = "datetime(6)")
    private Instant usedAt;

    @Column(name = "revoked_at", columnDefinition = "datetime(6)")
    private Instant revokedAt;

    @Column(name = "replacement_public_id", length = 36, columnDefinition = "char(36)")
    private String replacementPublicId;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    private RefreshToken(UserAccount account, byte[] tokenDigest, Instant expiresAt) {
        this.publicId = UUID.randomUUID().toString();
        this.account = account;
        this.tokenDigest = tokenDigest.clone();
        this.expiresAt = expiresAt;
    }

    /** 계정에 연결된 새 Refresh Token을 생성합니다. */
    public static RefreshToken issue(UserAccount account, byte[] tokenDigest, Instant expiresAt) {
        return new RefreshToken(account, tokenDigest, expiresAt);
    }

    /** 토큰을 한 번 사용하고 교체 토큰을 연결합니다. */
    public void markUsed(Instant usedAt, String replacementPublicId) {
        this.usedAt = usedAt;
        this.replacementPublicId = replacementPublicId;
    }

    /** Refresh Token을 폐기합니다. */
    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public boolean isUsableAt(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    @PrePersist
    private void onCreate() {
        createdAt = Instant.now();
    }
}
