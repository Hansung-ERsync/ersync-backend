package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TransportUpdateCommand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 이송 요청 단위 멱등 갱신 명령 영속성 접근점입니다. */
public interface TransportUpdateCommandRepository extends JpaRepository<TransportUpdateCommand, Long> {

    Optional<TransportUpdateCommand> findByTransportRequestIdAndIdempotencyKey(
            Long transportRequestId,
            String idempotencyKey
    );
}
