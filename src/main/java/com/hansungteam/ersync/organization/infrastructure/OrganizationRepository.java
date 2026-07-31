package com.hansungteam.ersync.organization.infrastructure;

import com.hansungteam.ersync.organization.domain.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 조직 영속성 접근점입니다. */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    Optional<Organization> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select organization from Organization organization where organization.publicId = :publicId")
    Optional<Organization> findLockedByPublicId(@Param("publicId") String publicId);
}
