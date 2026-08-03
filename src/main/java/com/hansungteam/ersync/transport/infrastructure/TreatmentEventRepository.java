package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.TreatmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/** 처치 이력 영속성 접근점입니다. */
public interface TreatmentEventRepository extends JpaRepository<TreatmentEvent, Long> {
}
