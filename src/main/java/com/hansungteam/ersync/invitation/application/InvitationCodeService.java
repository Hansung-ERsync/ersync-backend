package com.hansungteam.ersync.invitation.application;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.auth.infrastructure.UserAccountEntity;
import com.hansungteam.ersync.auth.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.invitation.domain.InvitationCodeStatus;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeEntity;
import com.hansungteam.ersync.invitation.infrastructure.InvitationCodeRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationEntity;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 가입 코드 발급, 조회, 폐기 유스케이스를 제공합니다.
 */
@Service
public class InvitationCodeService {

    private final InvitationCodeRepository invitationCodeRepository;
    private final OrganizationRepository organizationRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public InvitationCodeService(
            InvitationCodeRepository invitationCodeRepository,
            OrganizationRepository organizationRepository,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.invitationCodeRepository = invitationCodeRepository;
        this.organizationRepository = organizationRepository;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public IssuedInvitationResult issue(
            AuthenticatedAccount actor,
            String organizationId,
            UserRole targetRole,
            Instant expiresAt
    ) {
        Instant now = clock.instant();
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORGANIZATION_NOT_FOUND));
        if (!organization.active()) {
            throw new CustomException(ErrorCode.ORGANIZATION_INACTIVE);
        }
        validateRoleMatchesOrganization(targetRole, organization.type());
        if (!expiresAt.isAfter(now)) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }

        UserAccountEntity issuer = userAccountRepository.findById(actor.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        String plaintextCode = createPlaintextCode();
        InvitationCodeEntity invitationCode = invitationCodeRepository.save(new InvitationCodeEntity(
                organization,
                targetRole,
                passwordEncoder.encode(plaintextCode),
                expiresAt,
                issuer,
                now
        ));
        return IssuedInvitationResult.from(invitationCode, plaintextCode);
    }

    @Transactional(readOnly = true)
    public List<InvitationResult> findAll() {
        return invitationCodeRepository.findAll().stream()
                .map(code -> InvitationResult.from(code, clock.instant()))
                .toList();
    }

    @Transactional
    public InvitationResult revoke(AuthenticatedAccount actor, String invitationCodeId) {
        Instant now = clock.instant();
        InvitationCodeEntity invitationCode = invitationCodeRepository.findByIdForUpdate(invitationCodeId)
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_CODE_UNAVAILABLE));
        if (invitationCode.status() != InvitationCodeStatus.AVAILABLE) {
            throw new CustomException(ErrorCode.INVITATION_CODE_UNAVAILABLE);
        }
        UserAccountEntity revoker = userAccountRepository.findById(actor.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        invitationCode.revoke(revoker, now);
        return InvitationResult.from(invitationCode, now);
    }

    private String createPlaintextCode() {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void validateRoleMatchesOrganization(UserRole role, OrganizationType organizationType) {
        boolean matched = switch (role) {
            case PARAMEDIC -> organizationType == OrganizationType.EMS_UNIT;
            case HOSPITAL_STAFF -> organizationType == OrganizationType.HOSPITAL;
            case SUPER_ADMIN -> false;
        };
        if (!matched) {
            throw new CustomException(ErrorCode.INVITATION_ROLE_ORGANIZATION_MISMATCH);
        }
    }

    public record IssuedInvitationResult(
            String invitationCodeId,
            String organizationId,
            UserRole targetRole,
            InvitationCodeStatus status,
            Instant expiresAt,
            String plaintextCode
    ) {

        static IssuedInvitationResult from(InvitationCodeEntity code, String plaintextCode) {
            return new IssuedInvitationResult(
                    code.id(),
                    code.organization().id(),
                    code.targetRole(),
                    code.status(),
                    code.expiresAt(),
                    plaintextCode
            );
        }
    }

    public record InvitationResult(
            String invitationCodeId,
            String organizationId,
            String organizationName,
            UserRole targetRole,
            InvitationCodeStatus status,
            Instant expiresAt,
            Instant issuedAt,
            String usedBy,
            Instant usedAt,
            String revokedBy,
            Instant revokedAt
    ) {

        static InvitationResult from(InvitationCodeEntity code, Instant now) {
            InvitationCodeStatus status = code.status();
            if (status == InvitationCodeStatus.AVAILABLE && !code.expiresAt().isAfter(now)) {
                status = InvitationCodeStatus.EXPIRED;
            }
            return new InvitationResult(
                    code.id(),
                    code.organization().id(),
                    code.organization().name(),
                    code.targetRole(),
                    status,
                    code.expiresAt(),
                    code.issuedAt(),
                    code.usedBy() == null ? null : code.usedBy().id(),
                    code.usedAt(),
                    code.revokedBy() == null ? null : code.revokedBy().id(),
                    code.revokedAt()
            );
        }
    }
}
