package com.hansungteam.ersync.transport.destination.infrastructure;

import com.hansungteam.ersync.transport.destination.domain.TransportDestinationCommand;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 목적지 명령 이력과 멱등 결과 저장소입니다. */
public interface TransportDestinationCommandRepository extends JpaRepository<TransportDestinationCommand, Long> {

    long countByTransportRequestId(Long transportRequestId);

    @EntityGraph(attributePaths = {"previousDestinationOffer", "destinationOffer"})
    Optional<TransportDestinationCommand> findByTransportRequestIdAndIdempotencyKey(
            Long transportRequestId,
            String idempotencyKey
    );
}
