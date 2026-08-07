package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.SupplementalAssessmentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/** 추가 환자 평가 공통 원본의 영속성 접근점입니다. */
public interface SupplementalAssessmentRecordRepository
        extends JpaRepository<SupplementalAssessmentRecord, Long> {
}
