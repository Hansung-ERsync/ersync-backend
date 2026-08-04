package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.assessment.protocol.application.AssessmentProtocolValidator;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInputMapper;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.hospital.search.application.HospitalSearchService;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import com.hansungteam.ersync.paramedic.infrastructure.ParamedicProfileRepository;
import com.hansungteam.ersync.privacy.application.ContactSharingConsentPolicy;
import com.hansungteam.ersync.privacy.infrastructure.ContactSharingConsentRepository;
import com.hansungteam.ersync.transport.api.CreateTransportRequestRequest;
import com.hansungteam.ersync.transport.api.CreateTransportRequestResponse;
import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.IncidentAssessment;
import com.hansungteam.ersync.transport.domain.PatientDemographics;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.VitalSignSet;
import com.hansungteam.ersync.transport.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.IncidentAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.PatientDemographicsRepository;
import com.hansungteam.ersync.transport.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.transport.infrastructure.VitalSignSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** 인증 컨텍스트와 구조화된 임상 원본을 한 트랜잭션으로 이송 요청에 고정합니다. */
@Service
@RequiredArgsConstructor
public class TransportRequestService {

    private final UserAccountRepository userAccountRepository;
    private final ParamedicProfileRepository paramedicProfileRepository;
    private final ContactSharingConsentRepository contactSharingConsentRepository;
    private final ContactSharingConsentPolicy contactSharingConsentPolicy;
    private final AssessmentProtocolValidator assessmentProtocolValidator;
    private final ClinicalRecordMapper clinicalRecordMapper;
    private final TransportRequestFingerprint requestFingerprint;
    private final TransportRequestRepository transportRequestRepository;
    private final PatientDemographicsRepository patientDemographicsRepository;
    private final IncidentAssessmentRepository incidentAssessmentRepository;
    private final PreKtasAssessmentRepository preKtasAssessmentRepository;
    private final ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    private final VitalSignSetRepository vitalSignSetRepository;
    private final TreatmentEventRepository treatmentEventRepository;
    private final CurrentPatientSnapshotRepository currentPatientSnapshotRepository;
    private final HospitalSearchService hospitalSearchService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public TransportRequestCreationResult create(
            AuthenticatedAccount authenticated,
            String requestedIdempotencyKey,
            CreateTransportRequestRequest request
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        assessmentProtocolValidator.validate(request);
        byte[] fingerprint = requestFingerprint.digest(request);
        UserAccount account = requireParamedicContext(authenticated);
        ParamedicProfile profile = requireContactProfile(account);

        TransportRequest existing = transportRequestRepository
                .findByOwnerAccountPublicIdAndClientIdempotencyKey(account.getPublicId(), idempotencyKey)
                .orElse(null);
        if (existing != null) {
            if (!existing.hasSameFingerprint(fingerprint)) {
                throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
            }
            return new TransportRequestCreationResult(CreateTransportRequestResponse.from(existing), false);
        }

        Instant receivedAt = clock.instant();
        TransportRequest transportRequest = transportRequestRepository.saveAndFlush(TransportRequest.create(
                account,
                account.getOrganization(),
                profile.getContact(),
                request.assessmentProtocolVersion(),
                request.origin().latitude(),
                request.origin().longitude(),
                request.origin().source(),
                idempotencyKey,
                fingerprint,
                receivedAt
        ));

        PatientDemographics demographics = savePatientDemographics(transportRequest, request, receivedAt);
        IncidentAssessment incident = saveIncident(transportRequest, request, receivedAt);
        PreKtasAssessment preKtas = savePreKtas(transportRequest, account, request, receivedAt);
        ConsciousnessAssessment consciousness = saveConsciousness(
                transportRequest,
                account,
                request,
                receivedAt
        );
        VitalSignSet vitalSigns = saveVitalSigns(transportRequest, account, request, receivedAt);
        List<TreatmentEvent> treatments = saveTreatments(transportRequest, account, request, receivedAt);
        saveSnapshot(
                transportRequest,
                demographics,
                incident,
                preKtas,
                consciousness,
                vitalSigns,
                treatments,
                receivedAt
        );
        hospitalSearchService.initialize(transportRequest, receivedAt);

        auditService.record(
                AuditAction.TRANSPORT_REQUEST_CREATED,
                account,
                account.getOrganization(),
                "TRANSPORT_REQUEST",
                transportRequest.getPublicId(),
                receivedAt
        );
        return new TransportRequestCreationResult(CreateTransportRequestResponse.from(transportRequest), true);
    }

    private UserAccount requireParamedicContext(AuthenticatedAccount authenticated) {
        if (authenticated.role() != UserRole.PARAMEDIC) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        UserAccount account = userAccountRepository.findLockedByPublicId(authenticated.accountId())
                .orElseThrow(() -> new CustomException(ErrorCode.AUTH_AUTHENTICATION_REQUIRED));
        if (!account.isActive()) {
            throw new CustomException(ErrorCode.USER_INACTIVE);
        }
        if (account.getRole() != UserRole.PARAMEDIC) {
            throw new CustomException(ErrorCode.AUTH_ROLE_REQUIRED);
        }
        if (account.getOrganization() == null
                || !account.getOrganization().isActive()
                || account.getOrganization().getType() != OrganizationType.EMS_UNIT
                || authenticated.organizationId() == null
                || !authenticated.organizationId().equals(account.getOrganization().getPublicId())) {
            throw new CustomException(ErrorCode.COMMON_ACCESS_DENIED);
        }
        return account;
    }

    private ParamedicProfile requireContactProfile(UserAccount account) {
        ParamedicProfile profile = paramedicProfileRepository.findByAccountPublicId(account.getPublicId())
                .filter(found -> found.getOrganization().getPublicId().equals(account.getOrganization().getPublicId()))
                .orElseThrow(() -> new CustomException(ErrorCode.USER_CONTACT_OR_CONSENT_REQUIRED));
        boolean consentExists = contactSharingConsentRepository.existsByAccountPublicIdAndPolicyVersion(
                account.getPublicId(),
                contactSharingConsentPolicy.activePolicyVersion()
        );
        if (!consentExists) {
            throw new CustomException(ErrorCode.USER_CONTACT_OR_CONSENT_REQUIRED);
        }
        return profile;
    }

    private PatientDemographics savePatientDemographics(
            TransportRequest transportRequest,
            CreateTransportRequestRequest request,
            Instant receivedAt
    ) {
        return patientDemographicsRepository.save(PatientDemographics.create(
                transportRequest,
                request.patient().ageStatus(),
                request.patient().ageYears(),
                request.patient().sex(),
                receivedAt
        ));
    }

    private IncidentAssessment saveIncident(
            TransportRequest transportRequest,
            CreateTransportRequestRequest request,
            Instant receivedAt
    ) {
        return incidentAssessmentRepository.save(IncidentAssessment.create(
                transportRequest,
                request.incident().occurrenceType(),
                request.incident().mechanism(),
                trimToNull(request.incident().occurrenceDetail()),
                request.incident().injurySites() == null ? Set.of() : Set.copyOf(request.incident().injurySites()),
                request.incident().primarySymptom(),
                trimToNull(request.incident().primarySymptomDetail()),
                request.incident().secondarySymptoms() == null
                        ? Set.of()
                        : Set.copyOf(request.incident().secondarySymptoms()),
                request.incident().onsetTimeStatus(),
                request.incident().onsetAt(),
                request.incident().enteredAt(),
                receivedAt
        ));
    }

    private PreKtasAssessment savePreKtas(
            TransportRequest transportRequest,
            UserAccount account,
            CreateTransportRequestRequest request,
            Instant receivedAt
    ) {
        return preKtasAssessmentRepository.save(clinicalRecordMapper.preKtas(
                transportRequest, account, ClinicalInputMapper.from(request.preKtas()), receivedAt
        ));
    }

    private ConsciousnessAssessment saveConsciousness(
            TransportRequest transportRequest,
            UserAccount account,
            CreateTransportRequestRequest request,
            Instant receivedAt
    ) {
        return consciousnessAssessmentRepository.save(clinicalRecordMapper.consciousness(
                transportRequest, account, ClinicalInputMapper.from(request.consciousness()), receivedAt
        ));
    }

    private VitalSignSet saveVitalSigns(
            TransportRequest transportRequest,
            UserAccount account,
            CreateTransportRequestRequest request,
            Instant receivedAt
    ) {
        return vitalSignSetRepository.save(clinicalRecordMapper.vitalSigns(
                transportRequest, account, ClinicalInputMapper.from(request.vitalSigns()), receivedAt
        ));
    }

    private List<TreatmentEvent> saveTreatments(
            TransportRequest transportRequest,
            UserAccount account,
            CreateTransportRequestRequest request,
            Instant receivedAt
    ) {
        return ClinicalInputMapper.from(request.treatments()).stream()
                .map(treatment -> treatmentEventRepository.save(clinicalRecordMapper.treatment(
                        transportRequest, account, treatment, receivedAt
                )))
                .toList();
    }

    private void saveSnapshot(
            TransportRequest transportRequest,
            PatientDemographics demographics,
            IncidentAssessment incident,
            PreKtasAssessment preKtas,
            ConsciousnessAssessment consciousness,
            VitalSignSet vitalSigns,
            List<TreatmentEvent> treatments,
            Instant receivedAt
    ) {
        currentPatientSnapshotRepository.save(CurrentPatientSnapshot.create(
                transportRequest,
                demographics,
                incident,
                preKtas,
                consciousness,
                vitalSigns,
                treatments,
                transportRequest.getAssessmentProtocolVersion(),
                receivedAt,
                receivedAt
        ));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
