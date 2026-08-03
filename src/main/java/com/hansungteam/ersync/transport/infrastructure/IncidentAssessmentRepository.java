package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.IncidentAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

/** 환자 발생·증상 기록 영속성 접근점입니다. */
public interface IncidentAssessmentRepository extends JpaRepository<IncidentAssessment, Long> {
}
