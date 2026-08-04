package com.hansungteam.ersync.transport.destination.api;

import jakarta.validation.constraints.NotBlank;

/** 수락 병원 제안을 현재 목적지로 선택하는 요청입니다. */
public record SelectTransportDestinationRequest(
        @NotBlank String offerId
) {
}
