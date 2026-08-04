package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TransportLifecycleCommand;
import com.hansungteam.ersync.transport.domain.TransportLifecycleCommandType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 이송 종료 명령의 멱등 결과와 최근 목적지 이력을 조회합니다. */
public interface TransportLifecycleCommandRepository extends JpaRepository<TransportLifecycleCommand, Long> {

    @EntityGraph(attributePaths = {"destinationOffer", "destinationOffer.hospitalProfile"})
    Optional<TransportLifecycleCommand> findByTransportRequestIdAndIdempotencyKey(
            Long transportRequestId,
            String idempotencyKey
    );

    @EntityGraph(attributePaths = {"destinationOffer", "destinationOffer.hospitalProfile"})
    @Query("select command from TransportLifecycleCommand command "
            + "where command.transportRequest.id in :requestIds and command.commandType = :commandType")
    List<TransportLifecycleCommand> findByTransportRequestIdsAndCommandType(
            @Param("requestIds") Collection<Long> requestIds,
            @Param("commandType") TransportLifecycleCommandType commandType
    );

    long countByTransportRequestId(Long transportRequestId);
}
