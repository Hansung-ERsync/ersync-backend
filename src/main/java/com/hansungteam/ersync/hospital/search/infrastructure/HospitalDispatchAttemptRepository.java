package com.hansungteam.ersync.hospital.search.infrastructure;

import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttempt;
import com.hansungteam.ersync.hospital.search.domain.HospitalDispatchAttemptStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;

/** 병원 탐색 회차 영속성 접근점입니다. */
public interface HospitalDispatchAttemptRepository extends JpaRepository<HospitalDispatchAttempt, Long> {

    Optional<HospitalDispatchAttempt> findByTransportRequestPublicIdAndAttemptNumber(
            String transportRequestId,
            int attemptNumber
    );

    Optional<HospitalDispatchAttempt> findTopByTransportRequestPublicIdOrderByAttemptNumberDesc(
            String transportRequestId
    );

    Optional<HospitalDispatchAttempt> findByTransportRequestPublicIdAndRetryIdempotencyKey(
            String transportRequestId,
            String retryIdempotencyKey
    );

    Optional<HospitalDispatchAttempt> findTopByTransportRequestIdAndStatusOrderByAttemptNumberDesc(
            Long transportRequestId,
            HospitalDispatchAttemptStatus status
    );

    @Query("select attempt.transportRequest.id from HospitalDispatchAttempt attempt where attempt.id = :id")
    Optional<Long> findTransportRequestIdById(@Param("id") Long id);

    @Query("select attempt.id from HospitalDispatchAttempt attempt "
            + "where attempt.transportRequest.id = :transportRequestId and attempt.status = :status "
            + "order by attempt.attemptNumber desc")
    List<Long> findLatestIdsByTransportRequestIdAndStatus(
            @Param("transportRequestId") Long transportRequestId,
            @Param("status") HospitalDispatchAttemptStatus status,
            Pageable pageable
    );

    @Query("select attempt.id from HospitalDispatchAttempt attempt "
            + "where attempt.transportRequest.id = :transportRequestId and attempt.status = :status "
            + "order by attempt.id asc")
    List<Long> findIdsByTransportRequestIdAndStatusOrderById(
            @Param("transportRequestId") Long transportRequestId,
            @Param("status") HospitalDispatchAttemptStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select attempt from HospitalDispatchAttempt attempt "
            + "join fetch attempt.transportRequest where attempt.id = :id")
    Optional<HospitalDispatchAttempt> findLockedById(@Param("id") Long id);

    @Query("select attempt.id from HospitalDispatchAttempt attempt "
            + "where attempt.status = :status "
            + "and attempt.nextExpansionAt is not null "
            + "and attempt.nextExpansionAt <= :now order by attempt.nextExpansionAt asc")
    List<Long> findDueIds(
            @Param("status") HospitalDispatchAttemptStatus status,
            @Param("now") Instant now,
            org.springframework.data.domain.Pageable pageable
    );
}
