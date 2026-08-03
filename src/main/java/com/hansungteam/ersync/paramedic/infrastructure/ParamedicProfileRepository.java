package com.hansungteam.ersync.paramedic.infrastructure;

import com.hansungteam.ersync.paramedic.domain.ParamedicProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 구급대원 연락처 프로필 영속성 접근점입니다. */
public interface ParamedicProfileRepository extends JpaRepository<ParamedicProfile, Long> {

    @EntityGraph(attributePaths = {"account", "organization"})
    Optional<ParamedicProfile> findByAccountPublicId(String accountId);
}
