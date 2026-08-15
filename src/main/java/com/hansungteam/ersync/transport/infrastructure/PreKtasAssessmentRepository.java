package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Pre-KTAS 이력 영속성 접근점입니다. */
public interface PreKtasAssessmentRepository extends JpaRepository<PreKtasAssessment, Long> {

    List<PreKtasAssessment> findByPublicIdIn(Collection<String> publicIds);

    @Query("select assessment from PreKtasAssessment assessment "
            + "where assessment.transportRequest.id = :transportRequestId "
            + "and assessment.serverReceivedAt <= :cutoffAt "
            + "order by coalesce(assessment.assessedAt, assessment.enteredAt) desc, "
            + "assessment.serverReceivedAt desc, assessment.id desc")
    List<PreKtasAssessment> findLatestVisible(
            @Param("transportRequestId") Long transportRequestId,
            @Param("cutoffAt") Instant cutoffAt,
            Pageable pageable
    );
}
