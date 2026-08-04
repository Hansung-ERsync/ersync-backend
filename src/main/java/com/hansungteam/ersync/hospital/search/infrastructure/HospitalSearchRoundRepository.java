package com.hansungteam.ersync.hospital.search.infrastructure;

import com.hansungteam.ersync.hospital.search.domain.HospitalSearchRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 검색 반경 평가 이력 영속성 접근점입니다. */
public interface HospitalSearchRoundRepository extends JpaRepository<HospitalSearchRound, Long> {

    List<HospitalSearchRound> findByDispatchAttemptIdOrderByRadiusKmAsc(Long dispatchAttemptId);
}
