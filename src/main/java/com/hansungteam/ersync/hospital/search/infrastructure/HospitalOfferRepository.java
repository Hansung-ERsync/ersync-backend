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

    @Query("select offer.id from HospitalOffer offer "
            + "where offer.publicId = :publicId and offer.transportRequest.id = :transportRequestId")
    Optional<Long> findIdByPublicIdAndTransportRequestId(
            @Param("publicId") String publicId,
            @Param("transportRequestId") Long transportRequestId
    );

    @Query("select offer.transportRequest.id from HospitalOffer offer where offer.id = :id")
    Optional<Long> findTransportRequestIdById(@Param("id") Long id);

    @Query("select offer.id as offerId, offer.transportRequest.id as transportRequestId, "
            + "offer.dispatchAttempt.id as dispatchAttemptId from HospitalOffer offer "
            + "where offer.publicId = :publicId "
            + "and offer.hospitalProfile.organization.publicId = :organizationId")
    Optional<HospitalOfferLockScope> findLockScope(
            @Param("publicId") String publicId,
            @Param("organizationId") String organizationId
    );

    List<HospitalOffer> findByTransportRequestIdAndStatus(Long transportRequestId, HospitalOfferStatus status);

    @EntityGraph(attributePaths = {"transportRequest", "hospitalProfile", "hospitalProfile.organization"})
    List<HospitalOffer> findByTransportRequestIdAndStatusIn(
            Long transportRequestId,
            Collection<HospitalOfferStatus> statuses
    );

    @Query("select offer.id from HospitalOffer offer "
            + "where offer.transportRequest.id = :transportRequestId order by offer.id asc")
    List<Long> findIdsByTransportRequestIdOrderById(
            @Param("transportRequestId") Long transportRequestId
    );

    long countByTransportRequestIdAndStatus(Long transportRequestId, HospitalOfferStatus status);

    @Query("select distinct offer.hospitalProfile.id from HospitalOffer offer "
            + "where offer.transportRequest.id = :transportRequestId")
    List<Long> findContactedHospitalProfileIds(@Param("transportRequestId") Long transportRequestId);

    @Query("select distinct offer.hospitalProfile.id from HospitalOffer offer "
            + "where offer.transportRequest.id = :transportRequestId "
            + "and offer.status = com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.ACCEPTANCE_WITHDRAWN")
    List<Long> findWithdrawnHospitalProfileIds(@Param("transportRequestId") Long transportRequestId);

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

    @EntityGraph(attributePaths = {"transportRequest", "dispatchAttempt", "hospitalProfile"})
    @Query("select offer from HospitalOffer offer "
            + "where offer.hospitalProfile.id = :hospitalProfileId "
            + "and offer.closedAt is null "
            + "and offer.transportRequest.status not in ("
            + "com.hansungteam.ersync.transport.domain.TransportRequestStatus.COMPLETED, "
            + "com.hansungteam.ersync.transport.domain.TransportRequestStatus.CANCELLED) "
            + "and offer.status in ("
            + "com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.PENDING, "
            + "com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.ACCEPTED)")
    Page<HospitalOffer> findActiveForHospital(
            @Param("hospitalProfileId") Long hospitalProfileId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"transportRequest", "dispatchAttempt", "hospitalProfile"})
    @Query("select offer from HospitalOffer offer "
            + "where offer.hospitalProfile.id = :hospitalProfileId and ("
            + "offer.transportRequest.status in ("
            + "com.hansungteam.ersync.transport.domain.TransportRequestStatus.COMPLETED, "
            + "com.hansungteam.ersync.transport.domain.TransportRequestStatus.CANCELLED) or "
            + "offer.closedAt is not null or offer.status in ("
            + "com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.REJECTED, "
            + "com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.NO_RESPONSE, "
            + "com.hansungteam.ersync.hospital.search.domain.HospitalOfferStatus.ACCEPTANCE_WITHDRAWN))")
    Page<HospitalOffer> findHistoryForHospital(
            @Param("hospitalProfileId") Long hospitalProfileId,
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
            + "and offer.closedAt is null "
            + "and offer.etaNextAttemptAt is not null "
            + "and offer.etaNextAttemptAt <= :now order by offer.etaNextAttemptAt asc")
    List<Long> findRouteEstimateDueIds(@Param("now") java.time.Instant now, Pageable pageable);

    long countByDispatchAttemptIdAndStatus(Long dispatchAttemptId, HospitalOfferStatus status);

    long countByDispatchAttemptIdAndStatusIn(
            Long dispatchAttemptId,
            Collection<HospitalOfferStatus> statuses
    );

    interface HospitalOfferLockScope {
        Long getOfferId();

        Long getTransportRequestId();

        Long getDispatchAttemptId();
    }
}
