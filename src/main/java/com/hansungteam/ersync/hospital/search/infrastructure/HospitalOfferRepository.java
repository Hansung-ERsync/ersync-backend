package com.hansungteam.ersync.hospital.search.infrastructure;

import com.hansungteam.ersync.hospital.search.domain.HospitalOffer;
import com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 병원별 이송 요청 제안 영속성 접근점입니다. */
public interface HospitalOfferRepository extends JpaRepository<HospitalOffer, Long> {

    List<HospitalOffer> findByDispatchAttemptIdOrderByOfferedAtAsc(Long dispatchAttemptId);

    List<HospitalOffer> findByTransportRequestPublicIdOrderByOfferedAtAsc(String transportRequestId);

    @EntityGraph(attributePaths = {
            "transportRequest",
            "dispatchAttempt",
            "hospitalProfile",
            "hospitalProfile.organization"
    })
    Optional<HospitalOffer> findByPublicIdAndHospitalProfileOrganizationPublicId(
            String publicId,
            String organizationId
    );

    @EntityGraph(attributePaths = {"transportRequest", "dispatchAttempt", "hospitalProfile"})
    Page<HospitalOffer> findByHospitalProfileIdAndStatusIn(
            Long hospitalProfileId,
            Collection<HospitalOfferStatus> statuses,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select offer from HospitalOffer offer "
            + "join fetch offer.transportRequest "
            + "join fetch offer.dispatchAttempt "
            + "join fetch offer.hospitalProfile profile "
            + "join fetch profile.organization "
            + "where offer.id = :id")
    Optional<HospitalOffer> findLockedById(@Param("id") Long id);

    @Query("select offer.id from HospitalOffer offer "
            + "where offer.routeEstimateStatus = com.hansungteam.ersync.hospital.search.domain.RouteEstimateStatus.CALCULATING "
            + "and offer.etaNextAttemptAt is not null "
            + "and offer.etaNextAttemptAt <= :now order by offer.etaNextAttemptAt asc")
    List<Long> findRouteEstimateDueIds(@Param("now") java.time.Instant now, Pageable pageable);

    long countByDispatchAttemptIdAndStatus(Long dispatchAttemptId, HospitalOfferStatus status);

    long countByDispatchAttemptIdAndStatusIn(
            Long dispatchAttemptId,
            Collection<HospitalOfferStatus> statuses
    );
}
