package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.SupplementalAssessmentRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/** 추가 환자 평가 공통 원본의 영속성 접근점입니다. */
public interface SupplementalAssessmentRecordRepository
        extends JpaRepository<SupplementalAssessmentRecord, Long> {

    @EntityGraph(attributePaths = "generalAssessment")
    Optional<SupplementalAssessmentRecord>
            findFirstByTransportRequestIdAndServerReceivedAtLessThanEqualOrderByAssessedAtDescServerReceivedAtDescIdDesc(
                    Long transportRequestId,
                    Instant cutoffAt
            );
}
