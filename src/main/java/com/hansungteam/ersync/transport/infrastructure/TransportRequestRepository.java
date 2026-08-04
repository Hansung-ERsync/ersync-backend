package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TransportRequest;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

/** 이송 요청 영속성 접근점입니다. */
public interface TransportRequestRepository extends JpaRepository<TransportRequest, Long> {

    @EntityGraph(attributePaths = {"ownerAccount", "organization"})
    Optional<TransportRequest> findByOwnerAccountPublicIdAndClientIdempotencyKey(
            String ownerAccountId,
            String clientIdempotencyKey
    );

    Optional<TransportRequest> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"ownerAccount", "organization", "currentDestinationOffer"})
    Optional<TransportRequest> findByPublicIdAndOwnerAccountPublicId(String publicId, String ownerAccountId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"ownerAccount", "organization", "currentDestinationOffer"})
    @Query("select request from TransportRequest request "
            + "where request.publicId = :publicId and request.ownerAccount.publicId = :ownerAccountId")
    Optional<TransportRequest> findLockedOwnedByPublicId(
            @Param("publicId") String publicId,
            @Param("ownerAccountId") String ownerAccountId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"ownerAccount", "organization", "currentDestinationOffer"})
    @Query("select request from TransportRequest request where request.id = :id")
    Optional<TransportRequest> findLockedById(@Param("id") Long id);
}
