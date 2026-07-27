package com.hansungteam.ersync.organization.api;

import com.hansungteam.ersync.organization.application.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 슈퍼 관리자의 조직 관리 API입니다.
 */
@RestController
@RequestMapping("/api/v1/admin/organizations")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminOrganizationController {

    private final OrganizationService organizationService;

    public AdminOrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    /**
     * 병원 또는 구급대 조직을 등록합니다.
     *
     * @param request 등록할 조직 유형과 이름
     * @return 생성된 조직
     */
    @PostMapping
    public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
        return OrganizationResponse.from(organizationService.create(request.type(), request.name()));
    }

    /**
     * 등록된 조직 목록을 조회합니다.
     *
     * @return 조직 목록
     */
    @GetMapping
    public List<OrganizationResponse> findAll() {
        return organizationService.findAll().stream()
                .map(OrganizationResponse::from)
                .toList();
    }
}
