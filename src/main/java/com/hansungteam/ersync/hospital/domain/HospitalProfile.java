package com.hansungteam.ersync.hospital.domain;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.organization.domain.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 병원 응급실의 가입 정보와 신규 요청 수신 상태입니다. */
@Entity
@Table(name = "hospital_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HospitalProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false, unique = true)
    private Organization organization;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, unique = true)
    private UserAccount account;

    @Column(nullable = false, length = 255)
    private String address;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, length = 30)
    private String contact;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "receiving_status", nullable = false, length = 10)
    private ReceivingStatus receivingStatus;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private HospitalProfile(
            Organization organization,
            UserAccount account,
            String address,
            String detailAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String contact
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.organization = organization;
        this.account = account;
        this.address = address;
        this.detailAddress = detailAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.contact = contact;
        this.receivingStatus = ReceivingStatus.OFF;
    }

    /** 가입 직후 수신 OFF 상태의 병원 프로필을 생성합니다. */
    public static HospitalProfile create(
            Organization organization,
            UserAccount account,
            String address,
            String detailAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String contact
    ) {
        return new HospitalProfile(
                organization,
                account,
                address,
                detailAddress,
                latitude,
                longitude,
                contact
        );
    }

    /** 상세주소가 없는 기존 생성 계약을 유지합니다. */
    public static HospitalProfile create(
            Organization organization,
            UserAccount account,
            String address,
            BigDecimal latitude,
            BigDecimal longitude,
            String contact
    ) {
        return create(organization, account, address, null, latitude, longitude, contact);
    }

    /** 신규 요청 수신 상태를 변경합니다. */
    public void changeReceivingStatus(ReceivingStatus receivingStatus) {
        this.receivingStatus = receivingStatus;
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
