package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.account.domain.UserAccount;
import com.hansungteam.ersync.account.infrastructure.UserAccountRepository;
import com.hansungteam.ersync.assessment.protocol.application.AssessmentProtocolRegistry;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInput;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInputMapper;
import com.hansungteam.ersync.assessment.protocol.application.ClinicalInputValidator;
import com.hansungteam.ersync.audit.application.AuditService;
import com.hansungteam.ersync.audit.domain.AuditAction;
import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.global.security.AuthenticatedAccount;
import com.hansungteam.ersync.global.security.UserRole;
import com.hansungteam.ersync.organization.domain.OrganizationType;
import com.hansungteam.ersync.realtime.domain.RealtimeAudienceType;
import com.hansungteam.ersync.realtime.domain.RealtimeEventType;
import com.hansungteam.ersync.realtime.domain.RealtimeOutboxEvent;
import com.hansungteam.ersync.realtime.infrastructure.RealtimeOutboxEventRepository;
import com.hansungteam.ersync.transport.api.ClinicalUpdateResponse;
import com.hansungteam.ersync.transport.api.UpdateConsciousnessRequest;
import com.hansungteam.ersync.transport.api.UpdatePreKtasRequest;
import com.hansungteam.ersync.transport.api.UpdateTreatmentRequest;
import com.hansungteam.ersync.transport.api.UpdateVitalSignsRequest;
import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.TransportRequest;
import com.hansungteam.ersync.transport.domain.TransportRequestStatus;
import com.hansungteam.ersync.transport.domain.TransportUpdateCommand;
import com.hansungteam.ersync.transport.domain.TransportUpdateCommandType;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.VitalSignSet;
import com.hansungteam.ersync.transport.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.CurrentPatientSnapshotRepository;
import com.hansungteam.ersync.transport.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportRequestRepository;
import com.hansungteam.ersync.transport.infrastructure.TransportUpdateCommandRepository;
import com.hansungteam.ersync.transport.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.transport.infrastructure.VitalSignSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** 활성 이송 요청에 네 종류의 임상 원본을 멱등하게 추가합니다. */
@Service
@RequiredArgsConstructor
public class TransportClinicalUpdateService {

    private static final Set<TransportRequestStatus> UPDATABLE_STATUSES = EnumSet.of(
            TransportRequestStatus.SEARCHING,
            TransportRequestStatus.CANDIDATES_EXHAUSTED,
            TransportRequestStatus.ACCEPTED_AVAILABLE,
            TransportRequestStatus.EN_ROUTE
    );

    private final UserAccountRepository userAccountRepository;
    private final TransportRequestRepository transportRequestRepository;
    private final CurrentPatientSnapshotRepository currentPatientSnapshotRepository;
    private final TransportUpdateCommandRepository commandRepository;
    private final VitalSignSetRepository vitalSignSetRepository;
    private final ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    private final PreKtasAssessmentRepository preKtasAssessmentRepository;
    private final TreatmentEventRepository treatmentEventRepository;
    private final ClinicalInputValidator clinicalInputValidator;
    private final AssessmentProtocolRegistry protocolRegistry;
    private final ClinicalRecordMapper recordMapper;
    private final TransportUpdateFingerprint updateFingerprint;
    private final ClinicalAudienceResolver audienceResolver;
    private final RealtimeOutboxEventRepository outboxEventRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public ClinicalUpdateResult addVitalSigns(
            AuthenticatedAccount authenticated,
            String requestId,
            String requestedIdempotencyKey,
            UpdateVitalSignsRequest input
    ) {
        ClinicalInput.VitalSigns clinicalInput = ClinicalInputMapper.from(input);
        clinicalInputValidator.validateVitalSigns(clinicalInput);
        CommandContext context = requireContext(authenticated, requestId, requestedIdempotencyKey, input,
                TransportUpdateCommandType.VITAL_SIGNS);
        if (context.replay() != null) {
            return replay(context);
        }

        Instant receivedAt = clock.instant();
        VitalSignSet record = vitalSignSetRepository.save(
                recordMapper.vitalSigns(context.request(), context.account(), clinicalInput, receivedAt)
        );
        boolean snapshotUpdated = context.snapshot().advanceVitalSigns(record);
        return saveResult(context, record.getPublicId(), record.getMeasuredAt(), snapshotUpdated, receivedAt);
    }

    @Transactional
    public ClinicalUpdateResult addConsciousness(
            AuthenticatedAccount authenticated,
            String requestId,
            String requestedIdempotencyKey,
            UpdateConsciousnessRequest input
    ) {
        ClinicalInput.Consciousness clinicalInput = ClinicalInputMapper.from(input);
        clinicalInputValidator.validateConsciousness(clinicalInput);
        CommandContext context = requireContext(authenticated, requestId, requestedIdempotencyKey, input,
                TransportUpdateCommandType.CONSCIOUSNESS);
        if (context.replay() != null) {
            return replay(context);
        }

        Instant receivedAt = clock.instant();
        ConsciousnessAssessment record = consciousnessAssessmentRepository.save(
                recordMapper.consciousness(context.request(), context.account(), clinicalInput, receivedAt)
        );
        boolean snapshotUpdated = context.snapshot().advanceConsciousness(record);
        return saveResult(context, record.getPublicId(), record.getObservedAt(), snapshotUpdated, receivedAt);
    }

    @Transactional
    public ClinicalUpdateResult addPreKtas(
            AuthenticatedAccount authenticated,
            String requestId,
            String requestedIdempotencyKey,
            UpdatePreKtasRequest input
    ) {
        protocolRegistry.requirePreKtasStandardVersion(input.standardVersion());
        ClinicalInput.PreKtas clinicalInput = ClinicalInputMapper.from(input);
        clinicalInputValidator.validatePreKtas(clinicalInput);
        CommandContext context = requireContext(authenticated, requestId, requestedIdempotencyKey, input,
                TransportUpdateCommandType.PRE_KTAS);
        if (context.replay() != null) {
            return replay(context);
        }

        Instant receivedAt = clock.instant();
        PreKtasAssessment record = preKtasAssessmentRepository.save(
                recordMapper.preKtas(context.request(), context.account(), clinicalInput, receivedAt)
        );
        boolean snapshotUpdated = context.snapshot().advancePreKtas(record);
        Instant clinicalAt = record.getAssessedAt() == null ? record.getEnteredAt() : record.getAssessedAt();
        return saveResult(context, record.getPublicId(), clinicalAt, snapshotUpdated, receivedAt);
    }

    @Transactional
    public ClinicalUpdateResult addTreatment(
            AuthenticatedAccount authenticated,
            String requestId,
            String requestedIdempotencyKey,
            UpdateTreatmentRequest input
    ) {
        ClinicalInput.Treatment clinicalInput = ClinicalInputMapper.from(input);
        clinicalInputValidator.validateTreatment(clinicalInput, false);
        CommandContext context = requireContext(authenticated, requestId, requestedIdempotencyKey, input,
                TransportUpdateCommandType.TREATMENT);
        if (context.replay() != null) {
            return replay(context);
        }

        Instant receivedAt = clock.instant();
        TreatmentEvent record = treatmentEventRepository.save(
                recordMapper.treatment(context.request(), context.account(), clinicalInput, receivedAt)
        );
        context.snapshot().appendTreatment(record);
        return saveResult(context, record.getPublicId(), record.getPerformedAt(), true, receivedAt);
    }

    private CommandContext requireContext(
            AuthenticatedAccount authenticated,
            String requestId,
            String requestedIdempotencyKey,
            Object input,
            TransportUpdateCommandType type
    ) {
        String idempotencyKey = IdempotencyKeyPolicy.normalizeAndValidate(requestedIdempotencyKey);
        UserAccount account = requireParamedicAccount(authenticated);
        TransportRequest request = transportRequestRepository
                .findLockedOwnedByPublicId(requestId, account.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSPORT_REQUEST_NOT_FOUND));
        byte[] fingerprint = updateFingerprint.digest(type.name(), input);
        TransportUpdateCommand replay = commandRepository
                .findByTransportRequestIdAndIdempotencyKey(request.getId(), idempotencyKey)
                .orElse(null);
        if (replay != null && (replay.getCommandType() != type || !replay.hasSameFingerprint(fingerprint))) {
            throw new CustomException(ErrorCode.COMMON_DUPLICATE_CONFLICT);
        }
        if (replay == null) {
            requireUpdatable(request);
        }
        CurrentPatientSnapshot snapshot = currentPatientSnapshotRepository
                .findByTransportRequestPublicId(request.getPublicId())
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
        return new CommandContext(account, request, snapshot, type, idempotencyKey, fingerprint, replay);
    }

    private UserAccount requireParamedicAccount(AuthenticatedAccount authenticated) {
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

    private void requireUpdatable(TransportRequest request) {
        if (!UPDATABLE_STATUSES.contains(request.getStatus())) {
            throw new CustomException(ErrorCode.TRANSPORT_STATUS_CANNOT_CHANGE);
        }
    }

    private ClinicalUpdateResult saveResult(
            CommandContext context,
            String recordId,
            Instant clinicalAt,
            boolean snapshotUpdated,
            Instant receivedAt
    ) {
        currentPatientSnapshotRepository.save(context.snapshot());
        TransportUpdateCommand command = commandRepository.save(TransportUpdateCommand.clinical(
                context.request(), context.type(), context.idempotencyKey(), context.fingerprint(),
                recordId, clinicalAt, snapshotUpdated, receivedAt
        ));
        recordSignals(context, recordId, receivedAt);
        return new ClinicalUpdateResult(response(context.request(), context.snapshot(), command, false), true);
    }

    private void recordSignals(CommandContext context, String recordId, Instant receivedAt) {
        RealtimeEventType eventType = switch (context.type()) {
            case VITAL_SIGNS -> RealtimeEventType.VITAL_SIGNS_ADDED;
            case CONSCIOUSNESS -> RealtimeEventType.CONSCIOUSNESS_CHANGED;
            case PRE_KTAS -> RealtimeEventType.PRE_KTAS_CHANGED;
            case TREATMENT -> RealtimeEventType.TREATMENT_ADDED;
            case LOCATION -> throw new IllegalStateException("Clinical service cannot record a location signal");
        };
        AuditAction auditAction = switch (context.type()) {
            case VITAL_SIGNS -> AuditAction.VITAL_SIGNS_ADDED;
            case CONSCIOUSNESS -> AuditAction.CONSCIOUSNESS_CHANGED;
            case PRE_KTAS -> AuditAction.PRE_KTAS_CHANGED;
            case TREATMENT -> AuditAction.TREATMENT_ADDED;
            case LOCATION -> throw new IllegalStateException("Clinical service cannot audit a location signal");
        };
        for (String organizationId : audienceResolver.hospitalOrganizationIds(context.request())) {
            outboxEventRepository.save(RealtimeOutboxEvent.create(
                    eventType,
                    RealtimeAudienceType.ORGANIZATION,
                    organizationId,
                    "TRANSPORT_REQUEST",
                    context.request().getPublicId(),
                    receivedAt
            ));
        }
        auditService.record(
                auditAction,
                context.account(),
                context.account().getOrganization(),
                context.type().name(),
                recordId,
                receivedAt
        );
    }

    private ClinicalUpdateResult replay(CommandContext context) {
        return new ClinicalUpdateResult(response(context.request(), context.snapshot(), context.replay(), true), false);
    }

    private ClinicalUpdateResponse response(
            TransportRequest request,
            CurrentPatientSnapshot snapshot,
            TransportUpdateCommand command,
            boolean replay
    ) {
        return new ClinicalUpdateResponse(
                request.getPublicId(),
                command.getCommandType(),
                command.getResultRecordPublicId(),
                command.getResultClinicalAt(),
                command.getServerReceivedAt(),
                Boolean.TRUE.equals(command.getSnapshotUpdated()),
                snapshot.getLastClinicalUpdateAt(),
                replay
        );
    }

    private record CommandContext(
            UserAccount account,
            TransportRequest request,
            CurrentPatientSnapshot snapshot,
            TransportUpdateCommandType type,
            String idempotencyKey,
            byte[] fingerprint,
            TransportUpdateCommand replay
    ) {
    }
}
