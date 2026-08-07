package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.transport.api.TransportRequestDetailResponse;
import com.hansungteam.ersync.transport.api.TransportRequestView;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.IncidentAssessment;
import com.hansungteam.ersync.transport.domain.PatientDemographics;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/** 구급대원 본인의 진행 중 요청에서 최초정보와 최신 임상 snapshot을 조회합니다. */
@Service
@RequiredArgsConstructor
public class TransportRequestDetailQueryService {

    private final UserAccountRepository accountRepository;
    private final TransportRequestRepository requestRepository;
    private final CurrentPatientSnapshotRepository snapshotRepository;
    private final ClinicalSnapshotResponseMapper snapshotResponseMapper;
    private final SupplementalAssessmentResponseMapper supplementalAssessmentResponseMapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public TransportRequestDetailResponse detail(AuthenticatedAccount principal, String requestId) {
        UserAccount account = requireParamedic(principal);
        TransportRequest request = requestRepository
                .findByPublicIdAndOwnerAccountPublicIdAndStatusIn(
                        requestId, account.getPublicId(), TransportRequestView.ACTIVE.statuses()
                )
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        if (request.getOrganization() == null
                || !request.getOrganization().getPublicId().equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        CurrentPatientSnapshot snapshot = snapshotRepository
                .findByTransportRequestPublicId(request.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
        return response(request, snapshot);
    }

    private UserAccount requireParamedic(AuthenticatedAccount principal) {
        if (principal.role() != UserRole.PARAMEDIC || principal.organizationId() == null) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = accountRepository.findByPublicId(principal.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC
                || account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT
                || !account.getOrganization().getPublicId().equals(principal.organizationId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private TransportRequestDetailResponse response(
            TransportRequest request,
            CurrentPatientSnapshot snapshot
    ) {
        PatientDemographics patient = snapshot.getPatientDemographics();
        IncidentAssessment incident = snapshot.getIncidentAssessment();
        return new TransportRequestDetailResponse(
                request.getPublicId(),
                request.getStatus(),
                request.getAssessmentProtocolVersion(),
                new TransportRequestDetailResponse.Patient(
                        patient.getAgeStatus().name(), patient.getAgeYears(), patient.getSex().name()
                ),
                new TransportRequestDetailResponse.Incident(
                        incident.getOccurrenceType().name(),
                        incident.getOccurrenceDetail(),
                        enumName(incident.getMechanism()),
                        sortedNames(incident.getInjurySites()),
                        incident.getPrimarySymptom().name(),
                        incident.getPrimarySymptomDetail(),
                        sortedNames(incident.getSecondarySymptoms()),
                        incident.getOnsetTimeStatus().name(),
                        incident.getOnsetAt()
                ),
                snapshotResponseMapper.latest(snapshot),
                supplementalAssessmentResponseMapper.map(snapshot),
                request.getCreatedAt(),
                clock.instant()
        );
    }

    private List<String> sortedNames(Iterable<? extends Enum<?>> values) {
        ArrayList<String> names = new ArrayList<>();
        values.forEach(value -> names.add(value.name()));
        return names.stream().sorted().toList();
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
