package com.hansungteam.ersync.account.api;

import com.hansungteam.ersync.account.application.AccountSignupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 가입 코드로 병원 또는 구급대원 계정을 만드는 공개 API입니다. */
@RestController
@RequestMapping("/api/v1/auth/signups")
@RequiredArgsConstructor
public class AccountSignupController {

    private final AccountSignupService accountSignupService;

    @PostMapping("/hospital")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signupHospital(@Valid @RequestBody HospitalSignupRequest request) {
        return accountSignupService.signupHospital(request);
    }

    @PostMapping("/paramedic")
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signupParamedic(@Valid @RequestBody ParamedicSignupRequest request) {
        return accountSignupService.signupParamedic(request);
    }
}
