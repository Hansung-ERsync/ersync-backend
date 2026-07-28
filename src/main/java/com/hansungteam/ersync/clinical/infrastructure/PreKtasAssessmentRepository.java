package com.hansungteam.ersync.clinical.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Pre-KTAS append-only row 저장소입니다.
 */
public interface PreKtasAssessmentRepository extends JpaRepository<PreKtasAssessmentEntity, String> {

    long countByTransportRequestId(String transportRequestId);
}
