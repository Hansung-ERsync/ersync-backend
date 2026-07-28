package com.hansungteam.ersync.clinical.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 환자 평가 버전 row 저장소입니다.
 */
public interface PatientAssessmentVersionRepository extends JpaRepository<PatientAssessmentVersionEntity, String> {

    Optional<PatientAssessmentVersionEntity> findTopByTransportRequestIdOrderByVersionNumberDesc(String transportRequestId);

    long countByTransportRequestId(String transportRequestId);
}
