package com.hansungteam.ersync.transport.application;

import com.hansungteam.ersync.global.exception.CustomException;
import com.hansungteam.ersync.global.exception.ErrorCode;
import com.hansungteam.ersync.transport.api.SupplementalAssessmentResponse;
import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import com.hansungteam.ersync.transport.domain.SupplementalAssessmentRecord;
import com.hansungteam.ersync.transport.domain.SupplementalAssessmentType;
import org.springframework.stereotype.Component;

/** snapshot이 가리키는 타입별 추가 평가를 외부 공통 응답으로 변환합니다. */
@Component
public class SupplementalAssessmentResponseMapper {

    public SupplementalAssessmentResponse map(CurrentPatientSnapshot snapshot) {
        return map(snapshot.getLatestSupplementalAssessment());
    }

    public SupplementalAssessmentResponse map(ClinicalSnapshotView snapshot) {
        return map(snapshot.latestSupplementalAssessment());
    }

    private SupplementalAssessmentResponse map(SupplementalAssessmentRecord record) {
        if (record == null) {
            return null;
        }
        if (record.getAssessmentType() != SupplementalAssessmentType.GENERAL
                || record.getGeneralAssessment() == null) {
            throw new CustomException(ErrorCode.COMMON_INTERNAL_SERVER_ERROR);
        }

        var detail = record.getGeneralAssessment();
        return new SupplementalAssessmentResponse(
                record.getAssessedAt(),
                record.getEnteredAt(),
                record.getServerReceivedAt(),
                detail.getGlucoseMgDl(),
                detail.getLeftPupil(),
                detail.getRightPupil(),
                detail.getMedicalHistory(),
                detail.getAllergies(),
                detail.getMedications(),
                detail.getIsolationConcern()
        );
    }
}
