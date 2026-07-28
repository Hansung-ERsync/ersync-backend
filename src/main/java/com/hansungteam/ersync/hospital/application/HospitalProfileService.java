package com.hansungteam.ersync.hospital.application;

import com.hansungteam.ersync.auth.domain.AuthenticatedAccount;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.domain.ReceivingStatus;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileEntity;
import com.hansungteam.ersync.hospital.infrastructure.HospitalProfileRepository;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.organization.infrastructure.OrganizationEntity;
import com.hansungteam.ersync.organization.infrastructure.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

/**
 * 병원 공용 계정의 자기 병원 프로필과 응급실 수신 상태 유스케이스를 제공합니다.
 */
@Service
public class HospitalProfileService {

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    private final HospitalProfileRepository hospitalProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final Clock clock;

    public HospitalProfileService(
            HospitalProfileRepository hospitalProfileRepository,
            OrganizationRepository organizationRepository,
            Clock clock
    ) {
        this.hospitalProfileRepository = hospitalProfileRepository;
        this.organizationRepository = organizationRepository;
        this.clock = clock;
    }

    @Transactional
    public HospitalProfileResult upsertProfile(
            AuthenticatedAccount account,
            String erAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String erContact
    ) {
        requireHospitalStaff(account);
        validateCoordinate(latitude, longitude);
        Instant now = clock.instant();
        OrganizationEntity organization = hospitalOrganization(account.organizationId());

        HospitalProfileEntity profile = hospitalProfileRepository.findById(organization.id())
                .orElseGet(() -> new HospitalProfileEntity(
                        organization.id(),
                        erAddress.strip(),
                        latitude,
                        longitude,
                        erContact.strip(),
                        now
                ));
        if (hospitalProfileRepository.existsById(organization.id())) {
            profile.updateProfile(erAddress.strip(), latitude, longitude, erContact.strip(), now);
        }
        HospitalProfileEntity saved = hospitalProfileRepository.save(profile);
        return HospitalProfileResult.from(saved, organization.name());
    }

    @Transactional(readOnly = true)
    public HospitalProfileResult getProfile(AuthenticatedAccount account) {
        requireHospitalStaff(account);
        OrganizationEntity organization = hospitalOrganization(account.organizationId());
        HospitalProfileEntity profile = hospitalProfileRepository.findById(account.organizationId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        return HospitalProfileResult.from(profile, organization.name());
    }

    @Transactional
    public HospitalProfileResult changeReceivingStatus(
            AuthenticatedAccount account,
            ReceivingStatus receivingStatus
    ) {
        requireHospitalStaff(account);
        OrganizationEntity organization = hospitalOrganization(account.organizationId());
        HospitalProfileEntity profile = hospitalProfileRepository.findById(account.organizationId())
                .orElseThrow(() -> new CustomException(ErrorCode.HOSPITAL_NOT_FOUND));
        profile.changeReceivingStatus(receivingStatus, clock.instant());
        return HospitalProfileResult.from(profile, organization.name());
    }

    private void requireHospitalStaff(AuthenticatedAccount account) {
        if (account == null || account.role() != UserRole.HOSPITAL_STAFF || account.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
    }

    private OrganizationEntity hospitalOrganization(String organizationId) {
        OrganizationEntity organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new CustomException(ErrorCode.ORGANIZATION_NOT_FOUND));
        if (!organization.active() || organization.type() != OrganizationType.HOSPITAL) {
            throw new CustomException(ErrorCode.HOSPITAL_UNAVAILABLE);
        }
        return organization;
    }

    private void validateCoordinate(BigDecimal latitude, BigDecimal longitude) {
        if (latitude.compareTo(MIN_LATITUDE) < 0
                || latitude.compareTo(MAX_LATITUDE) > 0
                || longitude.compareTo(MIN_LONGITUDE) < 0
                || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new CustomException(ErrorCode.COMMON_REQUEST_VALIDATION_FAILED);
        }
    }

    public record HospitalProfileResult(
            String organizationId,
            String organizationName,
            String erAddress,
            BigDecimal latitude,
            BigDecimal longitude,
            String erContact,
            ReceivingStatus receivingStatus,
            Instant locationVerifiedAt,
            Instant updatedAt,
            long version
    ) {

        static HospitalProfileResult from(HospitalProfileEntity profile, String organizationName) {
            return new HospitalProfileResult(
                    profile.organizationId(),
                    organizationName,
                    profile.erAddress(),
                    profile.latitude(),
                    profile.longitude(),
                    profile.erContact(),
                    profile.receivingStatus(),
                    profile.locationVerifiedAt(),
                    profile.updatedAt(),
                    profile.version()
            );
        }
    }
}
