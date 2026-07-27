package com.hansungteam.ersync.organization.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationEntity;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

/**
 * 슈퍼 관리자 조직 관리 유스케이스를 제공합니다.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    public OrganizationService(OrganizationRepository organizationRepository, Clock clock) {
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Transactional
    public OrganizationResult create(OrganizationType type, String name) {
        try {
            OrganizationEntity organization = organizationRepository.save(new OrganizationEntity(
                    type,
                    name.strip(),
                    clock.instant()
            ));
            return OrganizationResult.from(organization);
        } catch (DataIntegrityViolationException ex) {
            throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
        }
    }

    @Transactional(readOnly = true)
    public List<OrganizationResult> findAll() {
        return organizationRepository.findAll().stream()
                .map(OrganizationResult::from)
                .toList();
    }

    public record OrganizationResult(
            String organizationId,
            OrganizationType type,
            String name,
            boolean active
    ) {

        static OrganizationResult from(OrganizationEntity organization) {
            return new OrganizationResult(
                    organization.id(),
                    organization.type(),
                    organization.name(),
                    organization.active()
            );
        }
    }
}
