package com.hansungteam.ersync.transport.destination.infrastructure;

import com.hansungteam.ersync.transport.destination.domain.TransportDestinationCommand;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 목적지 명령 이력과 멱등 결과 저장소입니다. */
public interface TransportDestinationCommandRepository extends JpaRepository<TransportDestinationCommand, Long> {

    long countByTransportRequestId(Long transportRequestId);

    @EntityGraph(attributePaths = {"previousDestinationOffer", "destinationOffer"})
    Optional<TransportDestinationCommand> findByTransportRequestIdAndIdempotencyKey(
            Long transportRequestId,
            String idempotencyKey
    );

    @Query("""
            select command.transportRequest.id as transportRequestId,
                   command.destinationOffer.id as destinationOfferId,
                   command.occurredAt as occurredAt
            from TransportDestinationCommand command
            where command.transportRequest.id in :transportRequestIds
              and command.resultType in (
                  com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType.SELECTED,
                  com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType.CHANGED
              )
              and command.id = (
                  select max(latest.id)
                  from TransportDestinationCommand latest
                  where latest.transportRequest.id = command.transportRequest.id
                    and latest.resultType in (
                        com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType.SELECTED,
                        com.hansungteam.ersync.transport.destination.domain.TransportDestinationResultType.CHANGED
                    )
              )
            """)
    List<LatestEffectiveDestination> findLatestEffectiveDestinations(
            @Param("transportRequestIds") Collection<Long> transportRequestIds
    );

    /** 요청별 마지막 실제 목적지 선택·변경 결과만 반환합니다. */
    interface LatestEffectiveDestination {

        Long getTransportRequestId();

        Long getDestinationOfferId();

        Instant getOccurredAt();
    }
}
