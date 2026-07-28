package com.hansungteam.ersync.clinical.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 활력징후 세트 append-only row 저장소입니다.
 */
public interface VitalSignSetRepository extends JpaRepository<VitalSignSetEntity, String> {

    long countByTransportRequestId(String transportRequestId);
}
