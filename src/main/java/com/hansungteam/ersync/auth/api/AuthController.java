package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.auth.application.AuthService;
import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 계정 가입, 로그인과 현재 인증 계정 조회 API입니다.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 가입 코드로 새 계정을 생성합니다.
     *
     * @param request 가입 코드와 계정 자격 증명
     * @return 생성된 계정 정보
     */
    @PostMapping("/signup")
    public SignupResponse signup(@Valid @RequestBody SignupRequest request) {
        return SignupResponse.from(authService.signup(
                request.invitationCode(),
                request.loginId(),
                request.password()
        ));
    }

    /**
     * 가입 코드가 연결된 조직과 역할을 확인합니다.
     *
     * @param request 확인할 가입 코드 원문
     * @return 가입 대상 조직과 역할
     */
    @PostMapping("/invitation-code/verify")
    public InvitationCodeVerificationResponse verifyInvitationCode(
            @Valid @RequestBody VerifyInvitationCodeRequest request
    ) {
        return InvitationCodeVerificationResponse.from(authService.previewInvitation(request.invitationCode()));
    }

    /**
     * 로그인 ID와 비밀번호를 검증하고 API 토큰을 발급합니다.
     *
     * @param request 로그인 자격 증명
     * @return Access Token과 Refresh Token
     */
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return LoginResponse.from(authService.login(request.loginId(), request.password()));
    }

    /**
     * 현재 Bearer 토큰으로 인증된 계정 정보를 반환합니다.
     *
     * @param account 인증 필터가 복원한 계정
     * @return 현재 계정 정보
     */
    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AuthenticatedAccount account) {
        return AccountResponse.from(authService.me(account));
    }
}
