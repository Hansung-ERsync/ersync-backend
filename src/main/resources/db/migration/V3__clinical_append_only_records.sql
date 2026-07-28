CREATE TABLE patient_assessment_versions (
    id VARCHAR(36) NOT NULL,
    transport_request_id VARCHAR(36) NOT NULL,
    version_number INT NOT NULL,
    age_status VARCHAR(16) NOT NULL,
    age_years INT NULL,
    sex VARCHAR(16) NOT NULL,
    occurrence_type VARCHAR(32) NOT NULL,
    occurrence_other_detail VARCHAR(120) NULL,
    mechanism VARCHAR(40) NULL,
    mechanism_other_detail VARCHAR(120) NULL,
    primary_symptom VARCHAR(40) NOT NULL,
    primary_symptom_other_detail VARCHAR(120) NULL,
    onset_time_status VARCHAR(16) NOT NULL,
    onset_at TIMESTAMP(6) NULL,
    last_known_well_status VARCHAR(16) NULL,
    last_known_well_at TIMESTAMP(6) NULL,
    accident_time_status VARCHAR(16) NULL,
    accident_at TIMESTAMP(6) NULL,
    cardiac_arrest_time_status VARCHAR(16) NULL,
    cardiac_arrest_at TIMESTAMP(6) NULL,
    entered_at TIMESTAMP(6) NOT NULL,
    server_received_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    supersedes_assessment_id VARCHAR(36) NULL,
    correction_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_patient_assessment_request_version
        UNIQUE (transport_request_id, version_number),
    CONSTRAINT fk_patient_assessment_supersedes
        FOREIGN KEY (supersedes_assessment_id) REFERENCES patient_assessment_versions (id)
);

CREATE INDEX idx_patient_assessment_transport_request_id
    ON patient_assessment_versions (transport_request_id);

CREATE TABLE patient_assessment_injury_sites (
    patient_assessment_id VARCHAR(36) NOT NULL,
    injury_site VARCHAR(40) NOT NULL,
    PRIMARY KEY (patient_assessment_id, injury_site),
    CONSTRAINT fk_patient_assessment_injury_sites_assessment
        FOREIGN KEY (patient_assessment_id) REFERENCES patient_assessment_versions (id)
);

CREATE TABLE patient_assessment_secondary_symptoms (
    patient_assessment_id VARCHAR(36) NOT NULL,
    symptom VARCHAR(40) NOT NULL,
    PRIMARY KEY (patient_assessment_id, symptom),
    CONSTRAINT fk_patient_assessment_secondary_symptoms_assessment
        FOREIGN KEY (patient_assessment_id) REFERENCES patient_assessment_versions (id)
);

CREATE TABLE pre_ktas_assessments (
    id VARCHAR(36) NOT NULL,
    transport_request_id VARCHAR(36) NOT NULL,
    classification_status VARCHAR(32) NOT NULL,
    level INT NULL,
    exception_reason VARCHAR(40) NULL,
    exception_detail VARCHAR(120) NULL,
    assessor_account_id VARCHAR(36) NOT NULL,
    assessed_at TIMESTAMP(6) NOT NULL,
    standard_version VARCHAR(80) NOT NULL,
    entered_at TIMESTAMP(6) NOT NULL,
    server_received_at TIMESTAMP(6) NOT NULL,
    supersedes_assessment_id VARCHAR(36) NULL,
    correction_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pre_ktas_supersedes
        FOREIGN KEY (supersedes_assessment_id) REFERENCES pre_ktas_assessments (id)
);

CREATE INDEX idx_pre_ktas_transport_request_id
    ON pre_ktas_assessments (transport_request_id);

CREATE TABLE consciousness_assessments (
    id VARCHAR(36) NOT NULL,
    transport_request_id VARCHAR(36) NOT NULL,
    avpu VARCHAR(20) NOT NULL,
    unassessable_reason VARCHAR(40) NULL,
    unassessable_detail VARCHAR(120) NULL,
    observed_at TIMESTAMP(6) NOT NULL,
    entered_at TIMESTAMP(6) NOT NULL,
    server_received_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    supersedes_assessment_id VARCHAR(36) NULL,
    correction_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_consciousness_supersedes
        FOREIGN KEY (supersedes_assessment_id) REFERENCES consciousness_assessments (id)
);

CREATE INDEX idx_consciousness_transport_request_id
    ON consciousness_assessments (transport_request_id);

CREATE TABLE vital_sign_sets (
    id VARCHAR(36) NOT NULL,
    transport_request_id VARCHAR(36) NOT NULL,
    blood_pressure_state VARCHAR(32) NOT NULL,
    systolic INT NULL,
    diastolic INT NULL,
    blood_pressure_unavailable_reason VARCHAR(40) NULL,
    blood_pressure_other_detail VARCHAR(120) NULL,
    pulse_state VARCHAR(32) NOT NULL,
    pulse_per_minute INT NULL,
    pulse_unavailable_reason VARCHAR(40) NULL,
    pulse_other_detail VARCHAR(120) NULL,
    respiratory_rate_state VARCHAR(32) NOT NULL,
    respirations_per_minute INT NULL,
    respiratory_unavailable_reason VARCHAR(40) NULL,
    respiratory_other_detail VARCHAR(120) NULL,
    temperature_state VARCHAR(32) NOT NULL,
    temperature_celsius DECIMAL(4, 1) NULL,
    temperature_unavailable_reason VARCHAR(40) NULL,
    temperature_other_detail VARCHAR(120) NULL,
    spo2_state VARCHAR(32) NOT NULL,
    spo2_percent INT NULL,
    spo2_unavailable_reason VARCHAR(40) NULL,
    spo2_other_detail VARCHAR(120) NULL,
    measured_at TIMESTAMP(6) NOT NULL,
    entered_at TIMESTAMP(6) NOT NULL,
    server_received_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    supersedes_vital_sign_set_id VARCHAR(36) NULL,
    correction_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_vital_sign_set_supersedes
        FOREIGN KEY (supersedes_vital_sign_set_id) REFERENCES vital_sign_sets (id)
);

CREATE INDEX idx_vital_sign_sets_transport_request_id
    ON vital_sign_sets (transport_request_id);

CREATE TABLE treatment_events (
    id VARCHAR(36) NOT NULL,
    transport_request_id VARCHAR(36) NOT NULL,
    type VARCHAR(40) NOT NULL,
    performed_at TIMESTAMP(6) NOT NULL,
    entered_at TIMESTAMP(6) NOT NULL,
    server_received_at TIMESTAMP(6) NOT NULL,
    created_by VARCHAR(36) NOT NULL,
    detail_schema_version VARCHAR(40) NOT NULL,
    details_json JSON NULL,
    supersedes_treatment_event_id VARCHAR(36) NULL,
    correction_reason VARCHAR(255) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_treatment_event_supersedes
        FOREIGN KEY (supersedes_treatment_event_id) REFERENCES treatment_events (id)
);

CREATE INDEX idx_treatment_events_transport_request_id
    ON treatment_events (transport_request_id);
