package com.hansungteam.ersync.privacy.domain;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 연락처를 상대 조직에 제공하는 데 동의한 사실과 문구 버전을 보관합니다. */
@Entity
@Table(name = "contact_sharing_consents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContactSharingConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false, length = 40)
    private ConsentType consentType;

    @Column(name = "policy_version", nullable = false, length = 50)
    private String policyVersion;

    @Column(name = "consented_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant consentedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    private ContactSharingConsent(
            UserAccount account,
            ConsentType consentType,
            String policyVersion,
            Instant consentedAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.account = account;
        this.consentType = consentType;
        this.policyVersion = policyVersion;
        this.consentedAt = consentedAt;
    }

    /** 사용자가 확인한 목적별 동의 문구 버전과 신뢰 가능한 서버 동의 시각을 기록합니다. */
    public static ContactSharingConsent record(
            UserAccount account,
            ConsentType consentType,
            String policyVersion,
            Instant consentedAt
    ) {
        return new ContactSharingConsent(account, consentType, policyVersion, consentedAt);
    }

    /** 기존 통합 동의 생성 호출은 수집·제공 통합 유형으로 보존합니다. */
    public static ContactSharingConsent record(
            UserAccount account,
            String policyVersion,
            Instant consentedAt
    ) {
        return record(
                account,
                ConsentType.CONTACT_COLLECTION_AND_PROVISION,
                policyVersion,
                consentedAt
        );
    }

    @PrePersist
    private void onCreate() {
        if (publicId == null) {
            publicId = UUID.randomUUID().toString();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
