package com.hansungteam.ersync.audit.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.audit.domain.AuditEvent;
import com.hansungteam.ersync.audit.infrastructure.AuditEventRepository;
import com.hansungteam.ersync.global.logging.TraceContext;
import com.hansungteam.ersync.organization.domain.Organization;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** 민감정보를 제외한 필수 감사 이벤트를 현재 트랜잭션에 저장합니다. */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public void record(
            AuditAction action,
            UserAccount actorAccount,
            Organization actorOrganization,
            String targetType,
            String targetPublicId,
            Instant occurredAt
    ) {
        auditEventRepository.save(AuditEvent.record(
                action,
                actorAccount,
                actorOrganization,
                targetType,
                targetPublicId,
                occurredAt,
                TraceContext.currentTraceId()
        ));
    }
}
