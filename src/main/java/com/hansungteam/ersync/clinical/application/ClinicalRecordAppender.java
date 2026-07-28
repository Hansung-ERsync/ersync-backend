package com.hansungteam.ersync.clinical.application;

import com.hansungteam.ersync.clinical.api.ConsciousnessAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PatientAssessmentRequest;
import com.hansungteam.ersync.clinical.api.PreKtasAssessmentRequest;
import com.hansungteam.ersync.clinical.api.TreatmentEventRequest;
import com.hansungteam.ersync.clinical.api.VitalSignSetRequest;
import com.hansungteam.ersync.clinical.infrastructure.ConsciousnessAssessmentEntity;
import com.hansungteam.ersync.clinical.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.clinical.infrastructure.PatientAssessmentVersionEntity;
import com.hansungteam.ersync.clinical.infrastructure.PatientAssessmentVersionRepository;
import com.hansungteam.ersync.clinical.infrastructure.PreKtasAssessmentEntity;
import com.hansungteam.ersync.clinical.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.clinical.infrastructure.TreatmentEventEntity;
import com.hansungteam.ersync.clinical.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.clinical.infrastructure.VitalSignSetEntity;
import com.hansungteam.ersync.clinical.infrastructure.VitalSignSetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * 검증된 임상 기록을 수정 없이 새 row로 추가합니다.
 */
@Service
public class ClinicalRecordAppender {

    private final ClinicalRecordValidator validator;
    private final PatientAssessmentVersionRepository patientAssessmentVersionRepository;
    private final PreKtasAssessmentRepository preKtasAssessmentRepository;
    private final ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    private final VitalSignSetRepository vitalSignSetRepository;
    private final TreatmentEventRepository treatmentEventRepository;
    private final Clock clock;

    public ClinicalRecordAppender(
            ClinicalRecordValidator validator,
            PatientAssessmentVersionRepository patientAssessmentVersionRepository,
            PreKtasAssessmentRepository preKtasAssessmentRepository,
            ConsciousnessAssessmentRepository consciousnessAssessmentRepository,
            VitalSignSetRepository vitalSignSetRepository,
            TreatmentEventRepository treatmentEventRepository,
            Clock clock
    ) {
        this.validator = validator;
        this.patientAssessmentVersionRepository = patientAssessmentVersionRepository;
        this.preKtasAssessmentRepository = preKtasAssessmentRepository;
        this.consciousnessAssessmentRepository = consciousnessAssessmentRepository;
        this.vitalSignSetRepository = vitalSignSetRepository;
        this.treatmentEventRepository = treatmentEventRepository;
        this.clock = clock;
    }

    @Transactional
    public PatientAssessmentVersionEntity appendPatientAssessment(
            String transportRequestId,
            String createdBy,
            PatientAssessmentRequest request
    ) {
        validator.validate(request);
        int nextVersion = patientAssessmentVersionRepository
                .findTopByTransportRequestIdOrderByVersionNumberDesc(transportRequestId)
                .map(PatientAssessmentVersionEntity::versionNumber)
                .orElse(0) + 1;
        return patientAssessmentVersionRepository.save(new PatientAssessmentVersionEntity(
                transportRequestId,
                nextVersion,
                createdBy,
                serverReceivedAt(),
                request
        ));
    }

    @Transactional
    public PreKtasAssessmentEntity appendPreKtas(
            String transportRequestId,
            String assessorAccountId,
            PreKtasAssessmentRequest request
    ) {
        validator.validate(request);
        return preKtasAssessmentRepository.save(new PreKtasAssessmentEntity(
                transportRequestId,
                assessorAccountId,
                serverReceivedAt(),
                request
        ));
    }

    @Transactional
    public ConsciousnessAssessmentEntity appendConsciousness(
            String transportRequestId,
            String createdBy,
            ConsciousnessAssessmentRequest request
    ) {
        validator.validate(request);
        return consciousnessAssessmentRepository.save(new ConsciousnessAssessmentEntity(
                transportRequestId,
                createdBy,
                serverReceivedAt(),
                request
        ));
    }

    @Transactional
    public VitalSignSetEntity appendVitalSignSet(
            String transportRequestId,
            String createdBy,
            VitalSignSetRequest request
    ) {
        validator.validate(request);
        return vitalSignSetRepository.save(new VitalSignSetEntity(
                transportRequestId,
                createdBy,
                serverReceivedAt(),
                request
        ));
    }

    @Transactional
    public List<TreatmentEventEntity> appendInitialTreatments(
            String transportRequestId,
            String createdBy,
            List<TreatmentEventRequest> requests
    ) {
        validator.validateInitialTreatments(requests);
        Instant serverReceivedAt = serverReceivedAt();
        return requests.stream()
                .map(request -> treatmentEventRepository.save(new TreatmentEventEntity(
                        transportRequestId,
                        createdBy,
                        serverReceivedAt,
                        request
                )))
                .toList();
    }

    private Instant serverReceivedAt() {
        return clock.instant();
    }
}
