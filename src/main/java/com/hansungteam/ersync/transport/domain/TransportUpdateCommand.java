package com.hansungteam.ersync.transport.domain;

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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

/** 민감한 원문 없이 갱신 결과와 SHA-256 지문만 보존하는 멱등 명령입니다. */
@Entity
@Table(name = "transport_update_commands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransportUpdateCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_request_id", nullable = false)
    private TransportRequest transportRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 30)
    private TransportUpdateCommandType commandType;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "binary(32)")
    private byte[] requestFingerprint;

    @Column(name = "result_record_public_id", nullable = false, length = 36, columnDefinition = "char(36)")
    private String resultRecordPublicId;

    @Column(name = "result_clinical_at", columnDefinition = "datetime(6)")
    private Instant resultClinicalAt;

    @Column(name = "snapshot_updated")
    private Boolean snapshotUpdated;

    @Column(name = "location_replaced")
    private Boolean locationReplaced;

    @Column(name = "server_received_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant serverReceivedAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "datetime(6)")
    private Instant createdAt;

    private TransportUpdateCommand(
            TransportRequest transportRequest,
            TransportUpdateCommandType commandType,
            String idempotencyKey,
            byte[] requestFingerprint,
            String resultRecordPublicId,
            Instant resultClinicalAt,
            Boolean snapshotUpdated,
            Boolean locationReplaced,
            Instant serverReceivedAt
    ) {
        this.publicId = UUID.randomUUID().toString();
        this.transportRequest = transportRequest;
        this.commandType = commandType;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = Arrays.copyOf(requestFingerprint, requestFingerprint.length);
        this.resultRecordPublicId = resultRecordPublicId;
        this.resultClinicalAt = resultClinicalAt;
        this.snapshotUpdated = snapshotUpdated;
        this.locationReplaced = locationReplaced;
        this.serverReceivedAt = serverReceivedAt;
        this.createdAt = serverReceivedAt;
    }

    public static TransportUpdateCommand clinical(
            TransportRequest request,
            TransportUpdateCommandType type,
            String idempotencyKey,
            byte[] fingerprint,
            String recordPublicId,
            Instant clinicalAt,
            boolean snapshotUpdated,
            Instant receivedAt
    ) {
        if (type == TransportUpdateCommandType.LOCATION) {
            throw new IllegalArgumentException("A clinical command cannot use LOCATION type");
        }
        return new TransportUpdateCommand(
                request, type, idempotencyKey, fingerprint, recordPublicId, clinicalAt,
                snapshotUpdated, null, receivedAt
        );
    }

    public static TransportUpdateCommand location(
            TransportRequest request,
            String idempotencyKey,
            byte[] fingerprint,
            String locationPublicId,
            boolean locationReplaced,
            Instant receivedAt
    ) {
        return new TransportUpdateCommand(
                request, TransportUpdateCommandType.LOCATION, idempotencyKey, fingerprint,
                locationPublicId, null, null, locationReplaced, receivedAt
        );
    }

    public boolean hasSameFingerprint(byte[] fingerprint) {
        return Arrays.equals(requestFingerprint, fingerprint);
    }
}
