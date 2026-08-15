package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.TreatmentType;
import com.hansungteam.ersync.transport.infrastructure.ConsciousnessAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.PreKtasAssessmentRepository;
import com.hansungteam.ersync.transport.infrastructure.SupplementalAssessmentRecordRepository;
import com.hansungteam.ersync.transport.infrastructure.TreatmentEventRepository;
import com.hansungteam.ersync.transport.infrastructure.VitalSignSetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/** append-only 임상 원본으로 현재 또는 지정 시점의 병원 공개 snapshot을 구성합니다. */
@Component
@RequiredArgsConstructor
public class ClinicalSnapshotReader {

    private final PreKtasAssessmentRepository preKtasAssessmentRepository;
    private final ConsciousnessAssessmentRepository consciousnessAssessmentRepository;
    private final VitalSignSetRepository vitalSignSetRepository;
    private final TreatmentEventRepository treatmentEventRepository;
    private final SupplementalAssessmentRecordRepository supplementalAssessmentRecordRepository;

    public ClinicalSnapshotView read(
            CurrentPatientSnapshot current,
            Instant cutoffAt,
            Instant frozenLastClinicalUpdateAt
    ) {
        if (cutoffAt == null) {
            return current(current);
        }
        requireFrozenLastUpdate(frozenLastClinicalUpdateAt);

        Long requestId = current.getTransportRequest().getId();
        var preKtas = latestPreKtas(requestId, cutoffAt);
        var consciousness = consciousnessAssessmentRepository
                .findFirstByTransportRequestIdAndServerReceivedAtLessThanEqualOrderByObservedAtDescServerReceivedAtDescIdDesc(
                        requestId,
                        cutoffAt
                )
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
        var vitalSigns = vitalSignSetRepository
                .findFirstByTransportRequestIdAndServerReceivedAtLessThanEqualOrderByMeasuredAtDescServerReceivedAtDescIdDesc(
                        requestId,
                        cutoffAt
                )
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
        List<TreatmentEvent> treatments = visibleTreatments(requestId, cutoffAt);
        var supplemental = supplementalAssessmentRecordRepository
                .findFirstByTransportRequestIdAndServerReceivedAtLessThanEqualOrderByAssessedAtDescServerReceivedAtDescIdDesc(
                        requestId,
                        cutoffAt
                )
                .orElse(null);
        return new ClinicalSnapshotView(
                current.getPatientDemographics(),
                current.getIncidentAssessment(),
                preKtas,
                consciousness,
                vitalSigns,
                treatments,
                supplemental,
                frozenLastClinicalUpdateAt
        );
    }

    public ClinicalSummaryView readSummary(
            CurrentPatientSnapshot current,
            Instant cutoffAt,
            Instant frozenLastClinicalUpdateAt
    ) {
        if (cutoffAt == null) {
            return new ClinicalSummaryView(
                    current.getPatientDemographics(),
                    current.getLatestPreKtasAssessment(),
                    current.getLastClinicalUpdateAt()
            );
        }
        requireFrozenLastUpdate(frozenLastClinicalUpdateAt);
        return new ClinicalSummaryView(
                current.getPatientDemographics(),
                latestPreKtas(current.getTransportRequest().getId(), cutoffAt),
                frozenLastClinicalUpdateAt
        );
    }

    private ClinicalSnapshotView current(CurrentPatientSnapshot current) {
        return new ClinicalSnapshotView(
                current.getPatientDemographics(),
                current.getIncidentAssessment(),
                current.getLatestPreKtasAssessment(),
                current.getLatestConsciousnessAssessment(),
                current.getLatestVitalSignSet(),
                List.copyOf(current.getCurrentTreatments()),
                current.getLatestSupplementalAssessment(),
                current.getLastClinicalUpdateAt()
        );
    }

    private List<TreatmentEvent> visibleTreatments(Long requestId, Instant cutoffAt) {
        List<TreatmentEvent> treatments = treatmentEventRepository
                .findByTransportRequestIdAndServerReceivedAtLessThanEqualOrderByServerReceivedAtAscIdAsc(
                        requestId,
                        cutoffAt
                );
        boolean hasActualTreatment = treatments.stream()
                .anyMatch(treatment -> treatment.getTreatmentType() != TreatmentType.NONE);
        if (!hasActualTreatment) {
            return treatments;
        }
        return treatments.stream()
                .filter(treatment -> treatment.getTreatmentType() != TreatmentType.NONE)
                .toList();
    }

    private PreKtasAssessment latestPreKtas(
            Long requestId,
            Instant cutoffAt
    ) {
        return preKtasAssessmentRepository
                .findLatestVisible(requestId, cutoffAt, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR));
    }

    private void requireFrozenLastUpdate(Instant frozenLastClinicalUpdateAt) {
        if (frozenLastClinicalUpdateAt == null) {
            throw new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR);
        }
    }
}
