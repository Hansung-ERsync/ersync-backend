package com.hansungteam.ersync.invitation.infrastructure;

import com.hansungteam.ersync.invitation.domain.InvitationCodeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * 가입 코드 영속성 접근을 담당합니다.
 */
public interface InvitationCodeRepository extends JpaRepository<InvitationCodeEntity, String> {

    List<InvitationCodeEntity> findByStatus(InvitationCodeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from InvitationCodeEntity code where code.status = :status")
    List<InvitationCodeEntity> findByStatusForUpdate(InvitationCodeStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from InvitationCodeEntity code where code.id = :id")
    Optional<InvitationCodeEntity> findByIdForUpdate(String id);
}
