package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

/** 의식 평가 이력 영속성 접근점입니다. */
public interface ConsciousnessAssessmentRepository extends JpaRepository<ConsciousnessAssessment, Long> {
}
