package com.hansungteam.ersync.auth.infrastructure;

import com.hansungteam.ersync.auth.domain.RefreshToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Refresh Token의 다이제스트 조회와 일회성 회전 잠금을 제공합니다. */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from RefreshToken token
            join fetch token.account account
            left join fetch account.organization
            where token.tokenDigest = :digest
            """)
    Optional<RefreshToken> findLockedByTokenDigest(@Param("digest") byte[] digest);
}
