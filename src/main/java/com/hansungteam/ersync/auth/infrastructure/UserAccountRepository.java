package com.hansungteam.ersync.auth.infrastructure;

import com.hansungteam.ersync.global.security.UserRole;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 계정 영속성 접근을 담당합니다.
 */
public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {

    Optional<UserAccountEntity> findByLoginId(String loginId);

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccountEntity> findWithOrganizationById(String id);

    boolean existsByLoginId(String loginId);

    boolean existsByRole(UserRole role);
}
