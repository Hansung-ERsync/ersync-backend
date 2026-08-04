package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.VitalSignSet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Collection;
import java.util.List;

/** 활력징후 세트와 항목의 영속성 접근점입니다. */
public interface VitalSignSetRepository extends JpaRepository<VitalSignSet, Long> {

    @EntityGraph(attributePaths = "measurements")
    List<VitalSignSet> findByPublicIdIn(Collection<String> publicIds);
}
