package com.hansungteam.ersync.clinical.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AVPU 의식 평가 append-only row 저장소입니다.
 */
public interface ConsciousnessAssessmentRepository extends JpaRepository<ConsciousnessAssessmentEntity, String> {

    long countByTransportRequestId(String transportRequestId);
}
