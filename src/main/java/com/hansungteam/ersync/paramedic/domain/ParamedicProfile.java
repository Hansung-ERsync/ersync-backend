package com.hansungteam.ersync.paramedic.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** 구급대원 계정의 병원 회신용 연락처를 보관합니다. */
@Entity
@Table(name = "paramedic_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParamedicProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(nullable = false, length = 30)
    private String contact;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    private ParamedicProfile(
            UserAccount account,
            Organization organization,
            String displayName,
            String contact
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.account = account;
        this.organization = organization;
        this.displayName = displayName;
        this.contact = contact;
    }

    /** 가입한 구급대원 계정의 화면 표시 이름과 연락처 프로필을 생성합니다. */
    public static ParamedicProfile create(
            UserAccount account,
            Organization organization,
            String displayName,
            String contact
    ) {
        return new ParamedicProfile(account, organization, displayName, contact);
    }

    /** 기존 테스트·Dev 계정은 로그인 ID를 표시 이름으로 사용합니다. */
    public static ParamedicProfile create(
            UserAccount account,
            Organization organization,
            String contact
    ) {
        return create(account, organization, account.getLoginId(), contact);
    }

    /** 계정·소속과 기존 동의는 유지하고 표시 이름과 회신 연락처만 함께 변경합니다. */
    public void updateDetails(String displayName, String contact) {
        this.displayName = displayName;
        this.contact = contact;
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
