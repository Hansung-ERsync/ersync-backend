package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.transport.api.ClinicalTimelineResponse;
import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import com.hansungteam.ersync.transport.domain.TreatmentDetails;
import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import com.hansungteam.ersync.transport.domain.VitalSignSet;
import org.springframework.stereotype.Component;

/** 상세와 timeline이 같은 최신 임상 JSON 의미를 사용하도록 DTO 변환을 공유합니다. */
@Component
public class ClinicalSnapshotResponseMapper {

    ClinicalTimelineResponse.LatestSnapshot latest(CurrentPatientSnapshot snapshot) {
        return latest(new ClinicalSnapshotView(
                snapshot.getPatientDemographics(),
                snapshot.getIncidentAssessment(),
                snapshot.getLatestPreKtasAssessment(),
                snapshot.getLatestConsciousnessAssessment(),
                snapshot.getLatestVitalSignSet(),
                snapshot.getCurrentTreatments(),
                snapshot.getLatestSupplementalAssessment(),
                snapshot.getLastClinicalUpdateAt()
        ));
    }

    ClinicalTimelineResponse.LatestSnapshot latest(ClinicalSnapshotView snapshot) {
        return new ClinicalTimelineResponse.LatestSnapshot(
                preKtas(snapshot.latestPreKtasAssessment()),
                consciousness(snapshot.latestConsciousnessAssessment()),
                vitalSigns(snapshot.latestVitalSignSet()),
                snapshot.currentTreatments().stream().map(this::treatment).toList(),
                snapshot.lastClinicalUpdateAt()
        );
    }

    ClinicalTimelineResponse.PreKtas preKtas(PreKtasAssessment record) {
        return new ClinicalTimelineResponse.PreKtas(
                record.getClassificationStatus().name(), record.getLevel(), enumName(record.getExceptionReason()),
                record.getExceptionDetail(), record.getAssessedAt(), record.getStandardVersion()
        );
    }

    ClinicalTimelineResponse.Consciousness consciousness(ConsciousnessAssessment record) {
        return new ClinicalTimelineResponse.Consciousness(
                record.getAvpu().name(), enumName(record.getUnassessableReason()),
                record.getUnassessableDetail(), record.getObservedAt()
        );
    }

    ClinicalTimelineResponse.VitalSigns vitalSigns(VitalSignSet record) {
        return new ClinicalTimelineResponse.VitalSigns(
                record.getMeasuredAt(),
                record.getMeasurements().stream().map(measurement -> new ClinicalTimelineResponse.VitalSign(
                        measurement.getMeasurementType().name(), measurement.getState().name(),
                        measurement.getPrimaryValue(), measurement.getSecondaryValue(),
                        enumName(measurement.getUnavailableReason()), measurement.getUnavailableDetail()
                )).toList()
        );
    }

    ClinicalTimelineResponse.Treatment treatment(TreatmentEvent record) {
        return new ClinicalTimelineResponse.Treatment(
                record.getTreatmentType().name(), enumName(record.getAttemptResult()), record.getPerformedAt(),
                details(record.getDetails())
        );
    }

    private ClinicalTimelineResponse.TreatmentDetails details(TreatmentDetails details) {
        if (details == null) {
            return null;
        }
        return new ClinicalTimelineResponse.TreatmentDetails(
                details.getMethod(), details.getDevice(), details.getFlowRateLpm(), details.getStartedAt(),
                details.getSuccess(), details.getCurrentStatus(), details.getRosc(), details.getRoscAt(),
                details.getShockCount(), details.getFluidName(), details.getAmountMl(),
                details.getMedicationName(), details.getDose(), details.getRoute(), details.getSite(),
                details.getTourniquetUsed(), details.getTourniquetAppliedAt(), details.getLeadType(),
                details.getFindings(), details.getTransmitted(), details.getBirthAt(), details.getDetail()
        );
    }

    private String enumName(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
