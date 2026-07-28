package com.hansungteam.ersync.clinical.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 처치 이벤트 append-only row 저장소입니다.
 */
public interface TreatmentEventRepository extends JpaRepository<TreatmentEventEntity, String> {

    long countByTransportRequestId(String transportRequestId);
}
