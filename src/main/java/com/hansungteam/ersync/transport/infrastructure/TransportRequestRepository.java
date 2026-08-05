package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    @EntityGraph(attributePaths = {"ownerAccount", "organization"})
    Optional<TransportRequest> findByPublicIdAndOwnerAccountPublicIdAndStatusIn(
            String publicId,
            String ownerAccountId,
            Collection<TransportRequestStatus> statuses
    );

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

    @EntityGraph(attributePaths = {"currentDestinationOffer"})
    @Query(
            value = "select request from TransportRequest request "
                    + "where request.ownerAccount.publicId = :ownerAccountId "
                    + "and request.status in :statuses "
                    + "order by case "
                    + "when request.status = com.hansungteam.ersync.transport.domain.TransportRequestStatus.COMPLETED "
                    + "then coalesce(request.completedAt, request.updatedAt) "
                    + "when request.status = com.hansungteam.ersync.transport.domain.TransportRequestStatus.CANCELLED "
                    + "then coalesce(request.cancelledAt, request.updatedAt) "
                    + "when request.status = com.hansungteam.ersync.transport.domain.TransportRequestStatus.HANDOFF_REQUESTED "
                    + "then coalesce(request.handoffRequestedAt, request.updatedAt) "
                    + "else request.updatedAt end desc, request.id desc",
            countQuery = "select count(request) from TransportRequest request "
                    + "where request.ownerAccount.publicId = :ownerAccountId and request.status in :statuses"
    )
    Page<TransportRequest> findPageByOwnerAndStatuses(
            @Param("ownerAccountId") String ownerAccountId,
            @Param("statuses") Collection<com.hansungteam.ersync.transport.domain.TransportRequestStatus> statuses,
            Pageable pageable
    );
}
