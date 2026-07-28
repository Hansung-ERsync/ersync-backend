package com.hansungteam.ersync.hospital.infrastructure;

import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 병원 응급실 주소, 좌표, 연락처와 새 요청 수신 상태를 저장합니다.
 */
@Entity
@Table(name = "hospital_profiles")
public class HospitalProfileEntity {

    @Id
    @Column(nullable = false, length = 36)
    private String organizationId;

    @Column(nullable = false, length = 255)
    private String erAddress;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false, length = 40)
    private String erContact;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ReceivingStatus receivingStatus;

    @Column(nullable = false)
    private Instant locationVerifiedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected HospitalProfileEntity() {
    }

    public HospitalProfileEntity(
            String organizationId,
            String erAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String erContact,
            Instant now
    ) {
        this.organizationId = organizationId;
        this.receivingStatus = ReceivingStatus.OFF;
        updateProfile(erAddress, latitude, longitude, erContact, now);
    }

    public void updateProfile(
            String erAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String erContact,
            Instant now
    ) {
        this.erAddress = erAddress;
        this.latitude = latitude;
        this.longitude = longitude;
        this.erContact = erContact;
        this.locationVerifiedAt = now;
        this.updatedAt = now;
    }

    public void changeReceivingStatus(ReceivingStatus receivingStatus, Instant now) {
        this.receivingStatus = receivingStatus;
        this.updatedAt = now;
    }

    public String organizationId() {
        return organizationId;
    }

    public String erAddress() {
        return erAddress;
    }

    public BigDecimal latitude() {
        return latitude;
    }

    public BigDecimal longitude() {
        return longitude;
    }

    public String erContact() {
        return erContact;
    }

    public ReceivingStatus receivingStatus() {
        return receivingStatus;
    }

    public Instant locationVerifiedAt() {
        return locationVerifiedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }
}
