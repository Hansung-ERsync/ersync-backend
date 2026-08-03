package com.hansungteam.ersync.assessment.protocol.api;

import com.hansungteam.ersync.assessment.protocol.application.AssessmentProtocolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증된 구급대원에게 현재 지원하는 개발용 평가 계약을 제공합니다. */
@RestController
@RequestMapping("/api/v1/assessment-protocols")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class AssessmentProtocolController {

    private final AssessmentProtocolRegistry assessmentProtocolRegistry;

    @GetMapping("/active")
    public AssessmentProtocolResponse active() {
        return assessmentProtocolRegistry.active();
    }
}
