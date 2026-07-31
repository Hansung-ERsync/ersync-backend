package com.hansungteam.ersync.hospital.infrastructure;

import com.hansungteam.ersync.hospital.domain.HospitalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 병원 프로필 영속성 접근점입니다. */
public interface HospitalProfileRepository extends JpaRepository<HospitalProfile, Long> {

    boolean existsByOrganizationPublicId(String organizationId);

    Optional<HospitalProfile> findByOrganizationPublicId(String organizationId);

    Optional<HospitalProfile> findByAccountPublicId(String accountId);
}
