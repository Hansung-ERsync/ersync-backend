package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.PreKtasAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** Pre-KTAS 이력 영속성 접근점입니다. */
public interface PreKtasAssessmentRepository extends JpaRepository<PreKtasAssessment, Long> {

    List<PreKtasAssessment> findByPublicIdIn(Collection<String> publicIds);
}
