package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.ClinicalRecordType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 기존 네 임상 원본을 DB에서 합쳐 시간순 페이지 색인으로 조회합니다. */
@Repository
public class ClinicalTimelineRepository {

    private static final String UNION = """
            SELECT 'VITAL_SIGNS' AS record_type, public_id AS record_public_id,
                   measured_at AS clinical_at, server_received_at
            FROM vital_sign_sets WHERE transport_request_id = ?
            UNION ALL
            SELECT 'CONSCIOUSNESS', public_id, observed_at, server_received_at
            FROM consciousness_assessments WHERE transport_request_id = ?
            UNION ALL
            SELECT 'PRE_KTAS', public_id, COALESCE(assessed_at, entered_at), server_received_at
            FROM pre_ktas_assessments WHERE transport_request_id = ?
            UNION ALL
            SELECT 'TREATMENT', public_id, COALESCE(performed_at, entered_at), server_received_at
            FROM treatment_events WHERE transport_request_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public ClinicalTimelineRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ClinicalTimelineRow> findPage(Long requestId, int offset, int limit) {
        String sql = "SELECT * FROM (" + UNION + ") timeline "
                + "ORDER BY clinical_at ASC, server_received_at ASC, record_public_id ASC LIMIT ? OFFSET ?";
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new ClinicalTimelineRow(
                        ClinicalRecordType.valueOf(resultSet.getString("record_type")),
                        resultSet.getString("record_public_id"),
                        instant(resultSet.getTimestamp("clinical_at")),
                        instant(resultSet.getTimestamp("server_received_at"))
                ),
                requestId, requestId, requestId, requestId, limit, offset
        );
    }

    public long count(Long requestId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + UNION + ") timeline",
                Long.class,
                requestId, requestId, requestId, requestId
        );
        return count == null ? 0 : count;
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
