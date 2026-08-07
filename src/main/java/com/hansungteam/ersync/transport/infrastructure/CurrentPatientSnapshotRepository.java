package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.CurrentPatientSnapshot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 요청별 최신 환자 snapshot 영속성 접근점입니다. */
public interface CurrentPatientSnapshotRepository extends JpaRepository<CurrentPatientSnapshot, Long> {

    @EntityGraph(attributePaths = {
            "transportRequest",
            "patientDemographics",
            "incidentAssessment",
            "latestPreKtasAssessment",
            "latestConsciousnessAssessment",
            "latestVitalSignSet",
            "currentTreatments",
            "latestSupplementalAssessment",
            "latestSupplementalAssessment.generalAssessment"
    })
    Optional<CurrentPatientSnapshot> findByTransportRequestPublicId(String transportRequestId);
}
