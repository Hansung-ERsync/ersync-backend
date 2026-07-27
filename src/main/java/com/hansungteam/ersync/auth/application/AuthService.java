package com.hansungteam.ersync.auth.application;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 공개 회원가입, 로그인과 인증 계정 조회 유스케이스를 제공합니다.
 */
@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuer tokenIssuer;
    private final Clock clock;

    public AuthService(
            UserAccountRepository userAccountRepository,
            InvitationCodeRepository invitationCodeRepository,
            PasswordEncoder passwordEncoder,
            TokenIssuer tokenIssuer,
            Clock clock
    ) {
        this.userAccountRepository = userAccountRepository;
        this.invitationCodeRepository = invitationCodeRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.clock = clock;
    }

    @Transactional
    public SignupResult signup(String invitationCode, String loginId, String password) {
        Instant now = clock.instant();
        InvitationCodeEntity code = findAvailableInvitationCode(invitationCode, now);
        validateRoleMatchesOrganization(code.targetRole(), code.organization().type());
        if (userAccountRepository.existsByLoginId(loginId)) {
            throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
        }

        try {
            UserAccountEntity account = userAccountRepository.save(new UserAccountEntity(
                    code.organization(),
                    code.targetRole(),
                    loginId,
                    passwordEncoder.encode(password),
                    now
            ));
            code.markUsed(account, now);
            return SignupResult.from(account);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
        }
    }

    @Transactional
    public LoginResult login(String loginId, String password) {
        Instant now = clock.instant();
        UserAccountEntity account = userAccountRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_LOGIN_FAILED));
        if (!account.active() || !passwordEncoder.matches(password, account.passwordHash())) {
            throw new CustomException(ErrorCode.AUTH_LOGIN_FAILED);
        }
        account.recordLogin(now);
        TokenIssuer.IssuedToken token = tokenIssuer.issue(account, now);
        return new LoginResult(
                token.accessToken(),
                "Bearer",
                token.expiresInSeconds(),
                token.refreshToken(),
                token.refreshTokenId(),
                token.refreshTokenExpiresAt(),
                AccountResult.from(account)
        );
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResult previewInvitation(String invitationCode) {
        InvitationCodeEntity code = invitationCodeRepository.findByStatus(InvitationCodeStatus.AVAILABLE).stream()
                .filter(candidate -> candidate.availableAt(clock.instant()))
                .filter(candidate -> passwordEncoder.matches(invitationCode, candidate.codeHash()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_CODE_UNAVAILABLE));
        return new InvitationPreviewResult(
                code.organization().id(),
                code.organization().name(),
                code.organization().type(),
                code.targetRole(),
                code.expiresAt()
        );
    }

    @Transactional(readOnly = true)
    public AccountResult me(AuthenticatedAccount authenticatedAccount) {
        UserAccountEntity account = userAccountRepository.findById(authenticatedAccount.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return AccountResult.from(account);
    }

    private InvitationCodeEntity findAvailableInvitationCode(String plaintextCode, Instant now) {
        return invitationCodeRepository.findByStatusForUpdate(InvitationCodeStatus.AVAILABLE).stream()
                .filter(code -> code.availableAt(now))
                .filter(code -> passwordEncoder.matches(plaintextCode, code.codeHash()))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.INVITATION_CODE_UNAVAILABLE));
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

    public record SignupResult(
            String accountId,
            String organizationId,
            UserRole role,
            String loginId
    ) {

        static SignupResult from(UserAccountEntity account) {
            return new SignupResult(
                    account.id(),
                    account.organization().id(),
                    account.role(),
                    account.loginId()
            );
        }
    }

    public record LoginResult(
            String accessToken,
            String tokenType,
            long expiresInSeconds,
            String refreshToken,
            String refreshTokenId,
            Instant refreshTokenExpiresAt,
            AccountResult account
    ) {
    }

    public record InvitationPreviewResult(
            String organizationId,
            String organizationName,
            OrganizationType organizationType,
            UserRole targetRole,
            Instant expiresAt
    ) {
    }

    public record AccountResult(
            String accountId,
            String organizationId,
            UserRole role,
            String loginId
    ) {

        static AccountResult from(UserAccountEntity account) {
            return new AccountResult(
                    account.id(),
                    account.organization() == null ? null : account.organization().id(),
                    account.role(),
                    account.loginId()
            );
        }
    }
}
