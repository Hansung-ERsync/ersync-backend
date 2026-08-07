package com.hansungteam.ersync.invitation.infrastructure;

import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** 가입 코드 조회와 동시성 잠금을 제공하는 영속성 접근점입니다. */
public interface InvitationCodeRepository
        extends JpaRepository<InvitationCode, Long>, JpaSpecificationExecutor<InvitationCode> {

    Optional<InvitationCode> findByPublicId(String publicId);

    boolean existsByCodeDigest(byte[] codeDigest);

    @EntityGraph(attributePaths = "organization")
    @Query("select invitation from InvitationCode invitation where invitation.codeDigest = :digest")
    Optional<InvitationCode> findByCodeDigest(@Param("digest") byte[] digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from InvitationCode invitation where invitation.publicId = :publicId")
    Optional<InvitationCode> findLockedByPublicId(@Param("publicId") String publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from InvitationCode invitation where invitation.codeDigest = :digest")
    Optional<InvitationCode> findLockedByCodeDigest(@Param("digest") byte[] digest);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<InvitationCode> findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
            InvitationStatus status,
            Instant expiresAt
    );
}
