package com.hansungteam.ersync.hospital.search.infrastructure;

import com.hansungteam.ersync.hospital.search.domain.HospitalOfferEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 병원 제안 불변 이벤트 영속성 접근점입니다. */
public interface HospitalOfferEventRepository extends JpaRepository<HospitalOfferEvent, Long> {

    List<HospitalOfferEvent> findByHospitalOfferDispatchAttemptIdOrderByOccurredAtAsc(Long dispatchAttemptId);
}
