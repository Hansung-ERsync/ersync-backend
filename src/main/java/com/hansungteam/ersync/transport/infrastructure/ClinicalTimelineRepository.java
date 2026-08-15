package com.hansungteam.ersync.transport.infrastructure;

import com.hansungteam.ersync.transport.domain.ClinicalRecordType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private static final String VISIBLE_UNION = """
            SELECT 'VITAL_SIGNS' AS record_type, public_id AS record_public_id,
                   measured_at AS clinical_at, server_received_at
            FROM vital_sign_sets WHERE transport_request_id = ? AND server_received_at <= ?
            UNION ALL
            SELECT 'CONSCIOUSNESS', public_id, observed_at, server_received_at
            FROM consciousness_assessments WHERE transport_request_id = ? AND server_received_at <= ?
            UNION ALL
            SELECT 'PRE_KTAS', public_id, COALESCE(assessed_at, entered_at), server_received_at
            FROM pre_ktas_assessments WHERE transport_request_id = ? AND server_received_at <= ?
            UNION ALL
            SELECT 'TREATMENT', public_id, COALESCE(performed_at, entered_at), server_received_at
            FROM treatment_events WHERE transport_request_id = ? AND server_received_at <= ?
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
                        instant(resultSet.getObject("clinical_at", LocalDateTime.class)),
                        instant(resultSet.getObject("server_received_at", LocalDateTime.class))
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

    public List<ClinicalTimelineRow> findPage(
            Long requestId,
            Instant cutoffAt,
            int offset,
            int limit
    ) {
        String sql = "SELECT * FROM (" + VISIBLE_UNION + ") timeline "
                + "ORDER BY clinical_at ASC, server_received_at ASC, record_public_id ASC LIMIT ? OFFSET ?";
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffAt, ZoneOffset.UTC);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> new ClinicalTimelineRow(
                        ClinicalRecordType.valueOf(resultSet.getString("record_type")),
                        resultSet.getString("record_public_id"),
                        instant(resultSet.getObject("clinical_at", LocalDateTime.class)),
                        instant(resultSet.getObject("server_received_at", LocalDateTime.class))
                ),
                requestId, cutoff,
                requestId, cutoff,
                requestId, cutoff,
                requestId, cutoff,
                limit, offset
        );
    }

    public long count(Long requestId, Instant cutoffAt) {
        LocalDateTime cutoff = LocalDateTime.ofInstant(cutoffAt, ZoneOffset.UTC);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + VISIBLE_UNION + ") timeline",
                Long.class,
                requestId, cutoff,
                requestId, cutoff,
                requestId, cutoff,
                requestId, cutoff
        );
        return count == null ? 0 : count;
    }

    private Instant instant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}
