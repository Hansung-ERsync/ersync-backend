package com.hansungteam.ersync.audit.infrastructure;

import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.domain.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** 감사 이벤트 영속성 접근점입니다. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    long countByAction(AuditAction action);
}
