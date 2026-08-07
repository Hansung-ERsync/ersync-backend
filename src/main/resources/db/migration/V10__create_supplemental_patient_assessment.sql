CREATE TABLE supplemental_assessment_records (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    assessment_type VARCHAR(40) NOT NULL,
    assessment_protocol_version VARCHAR(50) NOT NULL,
    assessed_at DATETIME(6) NOT NULL,
    entered_at DATETIME(6) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_supplemental_records_public_id UNIQUE (public_id),
    CONSTRAINT fk_supplemental_records_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_supplemental_records_creator
        FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_supplemental_records_type CHECK (
        assessment_type IN ('GENERAL')
    ),
    CONSTRAINT chk_supplemental_records_times CHECK (
        assessed_at <= entered_at
    )
);

CREATE INDEX idx_supplemental_records_request_type_time
    ON supplemental_assessment_records (
        transport_request_id,
        assessment_type,
        assessed_at,
        server_received_at
    );

CREATE TABLE general_supplemental_assessments (
    supplemental_assessment_id BIGINT NOT NULL,
    glucose_mg_dl INT NULL,
    left_pupil VARCHAR(30) NULL,
    right_pupil VARCHAR(30) NULL,
    medical_history VARCHAR(120) NULL,
    allergies VARCHAR(120) NULL,
    medications VARCHAR(120) NULL,
    isolation_concern BOOLEAN NULL,
    PRIMARY KEY (supplemental_assessment_id),
    CONSTRAINT fk_general_supplemental_record
        FOREIGN KEY (supplemental_assessment_id) REFERENCES supplemental_assessment_records (id),
    CONSTRAINT chk_general_supplemental_glucose CHECK (
        glucose_mg_dl IS NULL OR glucose_mg_dl BETWEEN 0 AND 1000
    ),
    CONSTRAINT chk_general_supplemental_pupil_values CHECK (
        (left_pupil IS NULL OR left_pupil IN ('NORMAL', 'SLUGGISH', 'FIXED', 'UNASSESSABLE'))
        AND
        (right_pupil IS NULL OR right_pupil IN ('NORMAL', 'SLUGGISH', 'FIXED', 'UNASSESSABLE'))
    ),
    CONSTRAINT chk_general_supplemental_pupil_pair CHECK (
        (left_pupil IS NULL AND right_pupil IS NULL)
        OR (left_pupil IS NOT NULL AND right_pupil IS NOT NULL)
    ),
    CONSTRAINT chk_general_supplemental_text CHECK (
        (medical_history IS NULL OR CHAR_LENGTH(TRIM(medical_history)) BETWEEN 1 AND 120)
        AND (allergies IS NULL OR CHAR_LENGTH(TRIM(allergies)) BETWEEN 1 AND 120)
        AND (medications IS NULL OR CHAR_LENGTH(TRIM(medications)) BETWEEN 1 AND 120)
    ),
    CONSTRAINT chk_general_supplemental_payload CHECK (
        glucose_mg_dl IS NOT NULL
        OR left_pupil IS NOT NULL
        OR medical_history IS NOT NULL
        OR allergies IS NOT NULL
        OR medications IS NOT NULL
        OR isolation_concern IS NOT NULL
    )
);

ALTER TABLE current_patient_snapshots
    ADD COLUMN latest_supplemental_assessment_id BIGINT NULL;

ALTER TABLE current_patient_snapshots
    ADD CONSTRAINT fk_current_snapshots_supplemental
        FOREIGN KEY (latest_supplemental_assessment_id) REFERENCES supplemental_assessment_records (id);
