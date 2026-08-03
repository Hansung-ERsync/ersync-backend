package com.hansungteam.ersync.account.infrastructure;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.global.security.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;

/** 사용자 계정 영속성 접근점입니다. */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByLoginId(String loginId);

    @EntityGraph(attributePaths = "organization")
    Optional<UserAccount> findByPublicId(String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = "organization")
    @Query("select account from UserAccount account where account.publicId = :publicId")
    Optional<UserAccount> findLockedByPublicId(@Param("publicId") String publicId);

    boolean existsByLoginId(String loginId);

    boolean existsByRole(UserRole role);

    long countByRole(UserRole role);
}
