package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TransportCurrentLocation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 요청별 단일 최신 위치 영속성 접근점입니다. */
public interface TransportCurrentLocationRepository extends JpaRepository<TransportCurrentLocation, Long> {

    @EntityGraph(attributePaths = "transportRequest")
    Optional<TransportCurrentLocation> findByTransportRequestId(Long transportRequestId);

    @EntityGraph(attributePaths = "transportRequest")
    Optional<TransportCurrentLocation> findByTransportRequestPublicId(String transportRequestPublicId);
}
