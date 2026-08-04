package com.hansungteam.ersync.transport.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 전체 이동 경로 없이 요청별 가장 최신인 정확한 위치 한 건만 유지합니다. */
@Entity
@Table(name = "transport_current_locations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportCurrentLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false, unique = true)
    private TransportRequest transportRequest;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "captured_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant capturedAt;

    @Column(name = "last_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant lastReceivedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    private TransportCurrentLocation(
            TransportRequest transportRequest,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant capturedAt,
            Instant receivedAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.latitude = latitude;
        this.longitude = longitude;
        this.capturedAt = capturedAt;
        this.lastReceivedAt = receivedAt;
        this.createdAt = receivedAt;
        this.updatedAt = receivedAt;
    }

    public static TransportCurrentLocation create(
            TransportRequest transportRequest,
            BigDecimal latitude,
            BigDecimal longitude,
            Instant capturedAt,
            Instant receivedAt
    ) {
        return new TransportCurrentLocation(transportRequest, latitude, longitude, capturedAt, receivedAt);
    }

    /** 더 오래된 단말 좌표는 무시하고 같은 시각은 서버 수신 순서대로 교체합니다. */
    public boolean replaceIfCurrent(
            BigDecimal latitude,
            BigDecimal longitude,
            Instant capturedAt,
            Instant receivedAt
    ) {
        if (capturedAt.isBefore(this.capturedAt)) {
            return false;
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.capturedAt = capturedAt;
        this.lastReceivedAt = receivedAt;
        this.updatedAt = receivedAt;
        return true;
    }
}
