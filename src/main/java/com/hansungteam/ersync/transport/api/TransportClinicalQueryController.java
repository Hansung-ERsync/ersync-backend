package com.hansungteam.ersync.transport.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.transport.application.ClinicalTimelineQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 구급대원이 자기 요청의 최신 임상 요약과 원본 이력을 조회하는 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests/{requestId}/clinical-timeline")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportClinicalQueryController {

    private final ClinicalTimelineQueryService queryService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping
    public ClinicalTimelineResponse timeline(
            @PathVariable String requestId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return queryService.ownerTimeline(currentAccountProvider.require(), requestId, page, size);
    }
}
