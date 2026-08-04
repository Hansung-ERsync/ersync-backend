package com.hansungteam.ersync.hospital.search.application;

import com.hansungteam.ersync.transport.domain.TransportRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 최신 위치 저장 기능이 생기기 전까지 요청 생성 좌표를 탐색 기준점으로 제공합니다. */
@Component
public class SearchOriginResolver {

    public SearchOrigin resolve(TransportRequest request) {
        return new SearchOrigin(request.getOriginLatitude(), request.getOriginLongitude());
    }

    public record SearchOrigin(BigDecimal latitude, BigDecimal longitude) {
    }
}
