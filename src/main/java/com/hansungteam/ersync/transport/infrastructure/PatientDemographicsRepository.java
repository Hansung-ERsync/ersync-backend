package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.PatientDemographics;
import org.springframework.data.jpa.repository.JpaRepository;

/** 환자 기본정보 영속성 접근점입니다. */
public interface PatientDemographicsRepository extends JpaRepository<PatientDemographics, Long> {
}
