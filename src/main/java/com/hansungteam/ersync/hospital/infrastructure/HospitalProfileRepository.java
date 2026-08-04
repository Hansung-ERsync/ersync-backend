package com.hansungteam.ersync.hospital.infrastructure;

import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.List;

/** 병원 프로필 영속성 접근점입니다. */
public interface HospitalProfileRepository extends JpaRepository<HospitalProfile, Long> {

    boolean existsByOrganizationPublicId(String organizationId);

    Optional<HospitalProfile> findByOrganizationPublicId(String organizationId);

    Optional<HospitalProfile> findByAccountPublicId(String accountId);

    @Query("select profile from HospitalProfile profile "
            + "join fetch profile.organization organization "
            + "join fetch profile.account account "
            + "where profile.receivingStatus = com.hansungteam.ersync.hospital.domain.ReceivingStatus.ON "
            + "and organization.status = com.hansungteam.ersync.organization.domain.OrganizationStatus.ACTIVE "
            + "and account.status = com.hansungteam.ersync.account.domain.AccountStatus.ACTIVE")
    List<HospitalProfile> findEligibleForNewRequests();
}
