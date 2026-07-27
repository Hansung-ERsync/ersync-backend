package com.hansungteam.ersync.auth.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Refresh Token 영속성 접근을 담당합니다.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, String> {
}
