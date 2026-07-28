package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.hospital.application.HospitalProfileService;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 병원 응급실 프로필 응답 DTO입니다.
 */
public record HospitalProfileResponse(
        String organizationId,
        String organizationName,
        String erAddress,
        BigDecimal latitude,
        BigDecimal longitude,
        String erContact,
        ReceivingStatus receivingStatus,
        Instant locationVerifiedAt,
        Instant updatedAt,
        long version
) {

    public static HospitalProfileResponse from(HospitalProfileService.HospitalProfileResult result) {
        return new HospitalProfileResponse(
                result.organizationId(),
                result.organizationName(),
                result.erAddress(),
                result.latitude(),
                result.longitude(),
                result.erContact(),
                result.receivingStatus(),
                result.locationVerifiedAt(),
                result.updatedAt(),
                result.version()
        );
    }
}
