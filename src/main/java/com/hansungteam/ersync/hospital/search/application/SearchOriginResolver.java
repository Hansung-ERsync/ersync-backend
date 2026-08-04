package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.infrastructure.TransportCurrentLocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 저장된 최신 위치를 우선하고 없으면 요청 생성 좌표를 탐색 기준점으로 제공합니다. */
@Component
@RequiredArgsConstructor
public class SearchOriginResolver {

    private final TransportCurrentLocationRepository locationRepository;

    public SearchOrigin resolve(TransportRequest request) {
        var location = locationRepository.findByTransportRequestId(request.getId()).orElse(null);
        if (location != null) {
            return new SearchOrigin(location.getLatitude(), location.getLongitude());
        }
        return new SearchOrigin(request.getOriginLatitude(), request.getOriginLongitude());
    }

    public record SearchOrigin(BigDecimal latitude, BigDecimal longitude) {
    }
}
