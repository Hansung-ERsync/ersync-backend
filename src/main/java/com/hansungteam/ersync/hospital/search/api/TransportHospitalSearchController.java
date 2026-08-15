package com.hansungteam.ersync.hospital.search.api;

import com.hansungteam.ersync.global.security.CurrentAccountProvider;
import com.hansungteam.ersync.hospital.search.application.TransportHospitalSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구급대원의 자기 요청 병원 탐색 현황 API입니다. */
@RestController
@RequestMapping("/api/v1/transport-requests/{transportRequestId}")
@RequiredArgsConstructor
@PreAuthorize("hasRole('PARAMEDIC')")
public class TransportHospitalSearchController {

    private final TransportHospitalSearchService searchService;
    private final CurrentAccountProvider currentAccountProvider;

    @GetMapping("/hospital-search")
    public TransportHospitalSearchResponse status(@PathVariable String transportRequestId) {
        return searchService.status(currentAccountProvider.require(), transportRequestId);
    }

}
