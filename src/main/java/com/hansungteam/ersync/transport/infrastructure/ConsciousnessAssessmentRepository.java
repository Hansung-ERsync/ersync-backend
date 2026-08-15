package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.ConsciousnessAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** 의식 평가 이력 영속성 접근점입니다. */
public interface ConsciousnessAssessmentRepository extends JpaRepository<ConsciousnessAssessment, Long> {

    List<ConsciousnessAssessment> findByPublicIdIn(Collection<String> publicIds);

    Optional<ConsciousnessAssessment>
            findFirstByTransportRequestIdAndServerReceivedAtLessThanEqualOrderByObservedAtDescServerReceivedAtDescIdDesc(
                    Long transportRequestId,
                    Instant cutoffAt
            );
}
