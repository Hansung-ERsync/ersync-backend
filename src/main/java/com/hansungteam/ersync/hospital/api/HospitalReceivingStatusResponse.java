package com.hansungteam.ersync.hospital.api;

import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;

import java.time.Instant;

/** 변경된 자기 병원의 신규 요청 수신 상태입니다. */
public record HospitalReceivingStatusResponse(
        String hospitalId,
        String organizationId,
        ReceivingStatus status,
        Instant updatedAt
) {

    public static HospitalReceivingStatusResponse from(HospitalProfile profile) {
        return new HospitalReceivingStatusResponse(
                profile.getPublicId(),
                profile.getOrganization().getPublicId(),
                profile.getReceivingStatus(),
                profile.getUpdatedAt()
        );
    }
}
