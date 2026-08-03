package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TransportRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 이송 요청 영속성 접근점입니다. */
public interface TransportRequestRepository extends JpaRepository<TransportRequest, Long> {

    @EntityGraph(attributePaths = {"ownerAccount", "organization"})
    Optional<TransportRequest> findByOwnerAccountPublicIdAndClientIdempotencyKey(
            String ownerAccountId,
            String clientIdempotencyKey
    );

    Optional<TransportRequest> findByPublicId(String publicId);
}
