package com.hansungteam.ersync.paramedic.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.paramedic.application.ParamedicProfileCommandService;
import com.hansungteam.ersync.paramedic.application.ParamedicProfileQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구급대원 앱의 로그인 복구에 사용하는 본인 프로필 API입니다. */
@RestController
@RequestMapping("/api/v1/paramedics/me")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class ParamedicProfileController {

    private final ParamedicProfileQueryService paramedicProfileQueryService;
    private final ParamedicProfileCommandService paramedicProfileCommandService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping
    public ParamedicProfileResponse getMine() {
        return paramedicProfileQueryService.getMine(currentAccountProvider.require());
    }

    @PutMapping
    public ParamedicProfileResponse updateMine(
            @Valid @RequestBody UpdateParamedicProfileRequest request
    ) {
        return paramedicProfileCommandService.update(currentAccountProvider.require(), request);
    }
}
