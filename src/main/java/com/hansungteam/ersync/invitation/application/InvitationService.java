package com.hansungteam.ersync.invitation.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.crypto.GeneratedSecret;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.api.InvitationExpiryOption;
import com.hansungteam.ersync.invitation.api.InvitationListResponse;
import com.hansungteam.ersync.invitation.api.InvitationResponse;
import com.hansungteam.ersync.invitation.api.IssueInvitationRequest;
import com.hansungteam.ersync.invitation.api.IssuedInvitationResponse;
import com.hansungteam.ersync.invitation.domain.InvitationCode;
import com.hansungteam.ersync.invitation.domain.InvitationStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 가입 코드의 발급, 조회, 폐기와 만료 상태 전이를 수행합니다. */
@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final InvitationCodeGenerator invitationCodeGenerator;
    private final AuditService auditService;
    private final Clock clock;

    /** 원문을 한 번만 반환하는 가입 코드를 발급합니다. */
    @Transactional
    public IssuedInvitationResponse issue(String actorAccountId, IssueInvitationRequest request) {
        UserAccount actor = requireSuperAdmin(actorAccountId);
        Organization organization = organizationRepository.findByPublicId(request.organizationId())
                .orElseThrow(() -> new CustomException(ErrorCode.ORGANIZATION_NOT_FOUND));
        validateRoleMapping(organization.getType(), request.role());

        Instant now = clock.instant();
        Instant expiresAt = resolveExpiresAt(request, now);
        GeneratedSecret generated = invitationCodeGenerator.generateUnique();
        InvitationCode invitation = invitationCodeRepository.save(InvitationCode.issue(
                organization,
                request.role(),
                generated.digest(),
                expiresAt,
                actor
        ));
        auditService.record(
                AuditAction.INVITATION_ISSUED,
                actor,
                null,
                "INVITATION_CODE",
                invitation.getPublicId(),
                now
        );
        return new IssuedInvitationResponse(generated.plainText(), InvitationResponse.from(invitation));
    }

    /** 원문과 다이제스트를 제외한 가입 코드 목록을 조회합니다. */
    @Transactional
    public InvitationListResponse list(
            InvitationStatus status,
            String organizationId,
            int page,
            int size
    ) {
        expireDueCodes();

        Specification<InvitationCode> specification = Specification.unrestricted();
        if (status != null) {
            specification = specification.and((root, query, builder) -> builder.equal(root.get("status"), status));
        }
        if (organizationId != null && !organizationId.isBlank()) {
            specification = specification.and((root, query, builder) -> builder.equal(
                    root.get("organization").get("publicId"),
                    organizationId
            ));
        }

        Page<InvitationResponse> result = invitationCodeRepository.findAll(
                        specification,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .map(InvitationResponse::from);
        return InvitationListResponse.from(result);
    }

    /** 아직 사용되지 않은 가입 코드를 폐기합니다. */
    @Transactional
    public InvitationResponse revoke(String actorAccountId, String invitationCodeId) {
        UserAccount actor = requireSuperAdmin(actorAccountId);
        InvitationCode invitation = invitationCodeRepository.findLockedByPublicId(invitationCodeId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_CODE_INVALID));
        if (invitation.getStatus() != InvitationStatus.AVAILABLE || invitation.hasExpiredAt(clock.instant())) {
            throw new CustomException(ErrorCode.INVITATION_STATUS_CANNOT_CHANGE);
        }

        Instant now = clock.instant();
        invitation.revoke(now);
        auditService.record(
                AuditAction.INVITATION_REVOKED,
                actor,
                null,
                "INVITATION_CODE",
                invitation.getPublicId(),
                now
        );
        return InvitationResponse.from(invitation);
    }

    /** 만료 시각이 지난 사용 전 코드를 멱등적으로 만료 처리합니다. */
    @Transactional
    public int expireDueCodes() {
        Instant now = clock.instant();
        List<InvitationCode> expired = invitationCodeRepository
                .findTop100ByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAsc(
                        InvitationStatus.AVAILABLE,
                        now
                );
        for (InvitationCode invitation : expired) {
            invitation.expire();
            auditService.record(
                    AuditAction.INVITATION_EXPIRED,
                    null,
                    null,
                    "INVITATION_CODE",
                    invitation.getPublicId(),
                    now
            );
        }
        return expired.size();
    }

    private UserAccount requireSuperAdmin(String actorAccountId) {
        UserAccount actor = userAccountRepository.findByPublicId(actorAccountId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!actor.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (actor.getRole() != UserRole.SUPER_ADMIN) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        return actor;
    }

    private void validateRoleMapping(OrganizationType organizationType, UserRole role) {
        boolean valid = (organizationType == OrganizationType.HOSPITAL && role == UserRole.HOSPITAL_STAFF)
                || (organizationType == OrganizationType.EMS_UNIT && role == UserRole.PARAMEDIC);
        if (!valid) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
    }

    private Instant resolveExpiresAt(IssueInvitationRequest request, Instant now) {
        return switch (request.expiryOption()) {
            case THREE_DAYS -> requireNoCustomExpiry(request, now.plus(Duration.ofDays(3)));
            case SEVEN_DAYS -> requireNoCustomExpiry(request, now.plus(Duration.ofDays(7)));
            case CUSTOM -> requireFutureCustomExpiry(request.customExpiresAt(), now);
        };
    }

    private Instant requireNoCustomExpiry(IssueInvitationRequest request, Instant expiresAt) {
        if (request.customExpiresAt() != null) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return expiresAt;
    }

    private Instant requireFutureCustomExpiry(Instant customExpiresAt, Instant now) {
        if (customExpiresAt == null || !customExpiresAt.isAfter(now)) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
        return customExpiresAt;
    }
}
