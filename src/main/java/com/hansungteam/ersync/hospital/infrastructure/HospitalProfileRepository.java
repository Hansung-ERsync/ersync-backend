package com.hansungteam.ersync.hospital.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 병원 프로필 영속성 접근을 담당합니다.
 */
public interface HospitalProfileRepository extends JpaRepository<HospitalProfileEntity, String> {
}
