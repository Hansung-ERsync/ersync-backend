package com.hansungteam.ersync.account.infrastructure;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.global.security.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

/** 사용자 계정 영속성 접근점입니다. */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByLoginId(String loginId);

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByPublicId(String publicId);

    boolean existsByLoginId(String loginId);

    boolean existsByRole(UserRole role);

    long countByRole(UserRole role);
}
