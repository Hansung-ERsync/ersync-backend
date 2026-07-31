package com.hansungteam.ersync.auth.api;

import com.hansungteam.ersync.auth.application.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인과 Access Token 갱신 공개 API입니다. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/tokens/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshAccessTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }
}
