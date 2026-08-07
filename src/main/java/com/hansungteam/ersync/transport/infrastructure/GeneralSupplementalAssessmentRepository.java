package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.GeneralSupplementalAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

/** GENERAL 추가 평가 상세의 영속성 접근점입니다. */
public interface GeneralSupplementalAssessmentRepository
        extends JpaRepository<GeneralSupplementalAssessment, Long> {
}
