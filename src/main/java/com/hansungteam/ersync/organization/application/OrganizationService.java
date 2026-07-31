package com.hansungteam.ersync.organization.application;

import com.hansungteam.ersync.organization.api.CreateOrganizationRequest;
import com.hansungteam.ersync.organization.api.OrganizationListResponse;
import com.hansungteam.ersync.organization.api.OrganizationResponse;
import com.hansungteam.ersync.organization.domain.Organization;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 슈퍼 관리자의 조직 등록과 목록 조회를 수행합니다. */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final int MAX_PAGE_SIZE = 100;
    private final OrganizationRepository organizationRepository;

    /** 병원 또는 구급대 조직을 등록합니다. */
    @Transactional
    public OrganizationResponse create(CreateOrganizationRequest request) {
        Organization organization = Organization.create(request.name().trim(), request.type());
        return OrganizationResponse.from(organizationRepository.save(organization));
    }

    /** 등록된 조직을 최신순으로 조회합니다. */
    @Transactional(readOnly = true)
    public OrganizationListResponse list(int page, int size) {
        int boundedSize = Math.min(size, MAX_PAGE_SIZE);
        Page<OrganizationResponse> result = organizationRepository.findAll(PageRequest.of(
                        page,
                        boundedSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                ))
                .map(OrganizationResponse::from);
        return OrganizationListResponse.from(result);
    }
}
