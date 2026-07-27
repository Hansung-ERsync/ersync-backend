package com.hansungteam.ersync.organization.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 조직 영속성 접근을 담당합니다.
 */
public interface OrganizationRepository extends JpaRepository<OrganizationEntity, String> {
}
