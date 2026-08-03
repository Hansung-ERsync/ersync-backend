CREATE TABLE paramedic_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    contact VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_paramedic_profiles_public_id UNIQUE (public_id),
    CONSTRAINT uk_paramedic_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_paramedic_profiles_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_paramedic_profiles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_paramedic_profiles_organization
    ON paramedic_profiles (organization_id);

CREATE TABLE contact_sharing_consents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    policy_version VARCHAR(50) NOT NULL,
    consented_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_contact_consents_public_id UNIQUE (public_id),
    CONSTRAINT uk_contact_consents_account_version UNIQUE (account_id, policy_version),
    CONSTRAINT fk_contact_consents_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_contact_consents_account_consented
    ON contact_sharing_consents (account_id, consented_at);

CREATE TABLE transport_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    owner_account_id BIGINT NOT NULL,
    organization_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    callback_contact VARCHAR(30) NOT NULL,
    assessment_protocol_version VARCHAR(50) NOT NULL,
    origin_latitude DECIMAL(10, 7) NOT NULL,
    origin_longitude DECIMAL(10, 7) NOT NULL,
    origin_source VARCHAR(30) NOT NULL,
    client_idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_transport_requests_public_id UNIQUE (public_id),
    CONSTRAINT uk_transport_requests_owner_key UNIQUE (owner_account_id, client_idempotency_key),
    CONSTRAINT fk_transport_requests_owner
        FOREIGN KEY (owner_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_transport_requests_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT chk_transport_requests_status CHECK (
        status IN (
            'SEARCHING', 'CANDIDATES_EXHAUSTED', 'ACCEPTED_AVAILABLE',
            'EN_ROUTE', 'HANDOFF_REQUESTED', 'COMPLETED', 'CANCELLED'
        )
    ),
    CONSTRAINT chk_transport_requests_origin_source
        CHECK (origin_source IN ('GPS', 'MANUAL_CONFIRMED')),
    CONSTRAINT chk_transport_requests_latitude
        CHECK (origin_latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_transport_requests_longitude
        CHECK (origin_longitude BETWEEN -180 AND 180)
);

CREATE INDEX idx_transport_requests_organization_status_created
    ON transport_requests (organization_id, status, created_at);

CREATE INDEX idx_transport_requests_owner_status_created
    ON transport_requests (owner_account_id, status, created_at);

CREATE TABLE patient_demographics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    age_status VARCHAR(20) NOT NULL,
    age_years INT NULL,
    sex VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_patient_demographics_public_id UNIQUE (public_id),
    CONSTRAINT uk_patient_demographics_request UNIQUE (transport_request_id),
    CONSTRAINT fk_patient_demographics_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT chk_patient_demographics_age_status
        CHECK (age_status IN ('EXACT', 'ESTIMATED', 'UNKNOWN')),
    CONSTRAINT chk_patient_demographics_age_value CHECK (
        (age_status IN ('EXACT', 'ESTIMATED') AND age_years IS NOT NULL AND age_years >= 0)
        OR (age_status = 'UNKNOWN' AND age_years IS NULL)
    ),
    CONSTRAINT chk_patient_demographics_sex
        CHECK (sex IN ('MALE', 'FEMALE', 'UNKNOWN'))
);

CREATE TABLE incident_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    occurrence_type VARCHAR(30) NOT NULL,
    mechanism VARCHAR(40) NULL,
    occurrence_detail VARCHAR(200) NULL,
    primary_symptom VARCHAR(40) NOT NULL,
    primary_symptom_detail VARCHAR(200) NULL,
    onset_time_status VARCHAR(20) NOT NULL,
    onset_at DATETIME(6) NULL,
    entered_at DATETIME(6) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_incident_assessments_public_id UNIQUE (public_id),
    CONSTRAINT uk_incident_assessments_request UNIQUE (transport_request_id),
    CONSTRAINT fk_incident_assessments_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT chk_incident_occurrence_type
        CHECK (occurrence_type IN ('DISEASE', 'NON_DISEASE', 'OTHER', 'UNKNOWN')),
    CONSTRAINT chk_incident_mechanism CHECK (
        mechanism IS NULL OR mechanism IN (
            'TRAFFIC', 'FALL', 'FALL_FROM_HEIGHT', 'BLUNT', 'PENETRATING',
            'BURN', 'POISONING', 'DROWNING_ASPHYXIA', 'ASSAULT_SELF_HARM',
            'MACHINERY_AGRICULTURAL', 'OTHER', 'UNKNOWN'
        )
    ),
    CONSTRAINT chk_incident_primary_symptom CHECK (
        primary_symptom IN (
            'ALTERED_CONSCIOUSNESS', 'DYSPNEA', 'RESPIRATORY_ARREST', 'CHEST_PAIN',
            'CARDIAC_ARREST', 'SUSPECTED_STROKE', 'SEIZURE_SYNCOPE', 'TRAUMA',
            'BLEEDING', 'GASTROINTESTINAL', 'POISONING', 'BURN',
            'PREGNANCY_DELIVERY', 'BEHAVIORAL_SELF_HARM', 'FEVER_INFECTION',
            'OTHER', 'UNKNOWN'
        )
    ),
    CONSTRAINT chk_incident_onset_status
        CHECK (onset_time_status IN ('EXACT', 'ESTIMATED', 'UNKNOWN')),
    CONSTRAINT chk_incident_onset_value CHECK (
        (onset_time_status IN ('EXACT', 'ESTIMATED') AND onset_at IS NOT NULL)
        OR (onset_time_status = 'UNKNOWN' AND onset_at IS NULL)
    )
);

CREATE TABLE incident_injury_sites (
    incident_assessment_id BIGINT NOT NULL,
    injury_site VARCHAR(30) NOT NULL,
    PRIMARY KEY (incident_assessment_id, injury_site),
    CONSTRAINT fk_incident_injury_sites_assessment
        FOREIGN KEY (incident_assessment_id) REFERENCES incident_assessments (id),
    CONSTRAINT chk_incident_injury_site CHECK (
        injury_site IN (
            'HEAD_FACE', 'NECK', 'CHEST', 'ABDOMEN_PELVIS', 'SPINE',
            'UPPER_LIMB', 'LOWER_LIMB', 'MULTIPLE', 'UNKNOWN'
        )
    )
);

CREATE TABLE incident_secondary_symptoms (
    incident_assessment_id BIGINT NOT NULL,
    symptom VARCHAR(40) NOT NULL,
    PRIMARY KEY (incident_assessment_id, symptom),
    CONSTRAINT fk_incident_secondary_symptoms_assessment
        FOREIGN KEY (incident_assessment_id) REFERENCES incident_assessments (id),
    CONSTRAINT chk_incident_secondary_symptom CHECK (
        symptom IN (
            'ALTERED_CONSCIOUSNESS', 'DYSPNEA', 'RESPIRATORY_ARREST', 'CHEST_PAIN',
            'CARDIAC_ARREST', 'SUSPECTED_STROKE', 'SEIZURE_SYNCOPE', 'TRAUMA',
            'BLEEDING', 'GASTROINTESTINAL', 'POISONING', 'BURN',
            'PREGNANCY_DELIVERY', 'BEHAVIORAL_SELF_HARM', 'FEVER_INFECTION',
            'OTHER', 'UNKNOWN'
        )
    )
);

CREATE TABLE pre_ktas_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    classification_status VARCHAR(30) NOT NULL,
    level INT NULL,
    exception_reason VARCHAR(40) NULL,
    exception_detail VARCHAR(200) NULL,
    assessed_at DATETIME(6) NULL,
    standard_version VARCHAR(50) NOT NULL,
    entered_at DATETIME(6) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_pre_ktas_assessments_public_id UNIQUE (public_id),
    CONSTRAINT fk_pre_ktas_assessments_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_pre_ktas_assessments_creator
        FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_pre_ktas_status
        CHECK (classification_status IN ('COMPLETED', 'EMERGENCY_UNFINISHED')),
    CONSTRAINT chk_pre_ktas_exception_reason CHECK (
        exception_reason IS NULL OR exception_reason IN (
            'CPR_IN_PROGRESS', 'SCENE_DANGER', 'INSUFFICIENT_ASSESSMENT_TIME', 'OTHER'
        )
    ),
    CONSTRAINT chk_pre_ktas_payload CHECK (
        (classification_status = 'COMPLETED'
            AND level BETWEEN 1 AND 5
            AND exception_reason IS NULL
            AND exception_detail IS NULL
            AND assessed_at IS NOT NULL)
        OR
        (classification_status = 'EMERGENCY_UNFINISHED'
            AND level IS NULL
            AND exception_reason IS NOT NULL)
    )
);

CREATE INDEX idx_pre_ktas_request_received
    ON pre_ktas_assessments (transport_request_id, server_received_at);

CREATE TABLE consciousness_assessments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    avpu VARCHAR(20) NOT NULL,
    unassessable_reason VARCHAR(30) NULL,
    unassessable_detail VARCHAR(200) NULL,
    observed_at DATETIME(6) NOT NULL,
    entered_at DATETIME(6) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_consciousness_assessments_public_id UNIQUE (public_id),
    CONSTRAINT fk_consciousness_assessments_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_consciousness_assessments_creator
        FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_consciousness_avpu
        CHECK (avpu IN ('A', 'V', 'P', 'U', 'UNASSESSABLE')),
    CONSTRAINT chk_consciousness_reason CHECK (
        unassessable_reason IS NULL OR unassessable_reason IN (
            'SCENE_DANGER', 'PATIENT_INACCESSIBLE', 'OTHER'
        )
    ),
    CONSTRAINT chk_consciousness_payload CHECK (
        (avpu <> 'UNASSESSABLE' AND unassessable_reason IS NULL AND unassessable_detail IS NULL)
        OR (avpu = 'UNASSESSABLE' AND unassessable_reason IS NOT NULL)
    )
);

CREATE INDEX idx_consciousness_request_received
    ON consciousness_assessments (transport_request_id, server_received_at);

CREATE TABLE vital_sign_sets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    measured_at DATETIME(6) NOT NULL,
    entered_at DATETIME(6) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vital_sign_sets_public_id UNIQUE (public_id),
    CONSTRAINT fk_vital_sign_sets_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_vital_sign_sets_creator
        FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_vital_sign_sets_request_received
    ON vital_sign_sets (transport_request_id, server_received_at);

CREATE TABLE vital_sign_measurements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    vital_sign_set_id BIGINT NOT NULL,
    measurement_type VARCHAR(30) NOT NULL,
    state VARCHAR(30) NOT NULL,
    primary_value DECIMAL(10, 3) NULL,
    secondary_value DECIMAL(10, 3) NULL,
    unavailable_reason VARCHAR(30) NULL,
    unavailable_detail VARCHAR(200) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_vital_measurements_set_type UNIQUE (vital_sign_set_id, measurement_type),
    CONSTRAINT fk_vital_measurements_set
        FOREIGN KEY (vital_sign_set_id) REFERENCES vital_sign_sets (id),
    CONSTRAINT chk_vital_measurement_type CHECK (
        measurement_type IN (
            'BLOOD_PRESSURE', 'PULSE', 'RESPIRATORY_RATE', 'TEMPERATURE', 'SPO2'
        )
    ),
    CONSTRAINT chk_vital_measurement_state
        CHECK (state IN ('VALUE', 'MEASUREMENT_UNAVAILABLE', 'PATIENT_REFUSED')),
    CONSTRAINT chk_vital_unavailable_reason CHECK (
        unavailable_reason IS NULL OR unavailable_reason IN (
            'PATIENT_CONDITION', 'SCENE_DANGER', 'INJURY_SITE', 'DEVICE_ERROR', 'OTHER'
        )
    ),
    CONSTRAINT chk_vital_measurement_payload CHECK (
        (state = 'VALUE'
            AND primary_value IS NOT NULL
            AND unavailable_reason IS NULL
            AND unavailable_detail IS NULL
            AND (
                (measurement_type = 'BLOOD_PRESSURE' AND secondary_value IS NOT NULL)
                OR (measurement_type <> 'BLOOD_PRESSURE' AND secondary_value IS NULL)
            ))
        OR
        (state = 'MEASUREMENT_UNAVAILABLE'
            AND primary_value IS NULL
            AND secondary_value IS NULL
            AND unavailable_reason IS NOT NULL)
        OR
        (state = 'PATIENT_REFUSED'
            AND primary_value IS NULL
            AND secondary_value IS NULL
            AND unavailable_reason IS NULL
            AND unavailable_detail IS NULL)
    )
);

CREATE TABLE treatment_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    treatment_type VARCHAR(40) NOT NULL,
    attempt_result VARCHAR(30) NULL,
    treatment_method VARCHAR(100) NULL,
    device VARCHAR(100) NULL,
    flow_rate_lpm DECIMAL(8, 2) NULL,
    started_at DATETIME(6) NULL,
    success BOOLEAN NULL,
    current_status VARCHAR(50) NULL,
    rosc BOOLEAN NULL,
    rosc_at DATETIME(6) NULL,
    shock_count INT NULL,
    fluid_name VARCHAR(100) NULL,
    amount_ml DECIMAL(10, 2) NULL,
    medication_name VARCHAR(100) NULL,
    dose VARCHAR(50) NULL,
    route VARCHAR(50) NULL,
    treatment_site VARCHAR(100) NULL,
    tourniquet_used BOOLEAN NULL,
    tourniquet_applied_at DATETIME(6) NULL,
    lead_type VARCHAR(20) NULL,
    findings VARCHAR(200) NULL,
    transmitted BOOLEAN NULL,
    birth_at DATETIME(6) NULL,
    detail VARCHAR(300) NULL,
    performed_at DATETIME(6) NULL,
    entered_at DATETIME(6) NOT NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_by_account_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_treatment_events_public_id UNIQUE (public_id),
    CONSTRAINT fk_treatment_events_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_treatment_events_creator
        FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_treatment_type CHECK (
        treatment_type IN (
            'NONE', 'OXYGEN', 'AIRWAY', 'CPR', 'DEFIBRILLATION_AED', 'IV_FLUID',
            'MEDICATION', 'BLEEDING_WOUND', 'IMMOBILIZATION', 'ECG',
            'WARMING_COOLING', 'DELIVERY', 'OTHER'
        )
    ),
    CONSTRAINT chk_treatment_attempt_result CHECK (
        attempt_result IS NULL OR attempt_result IN (
            'SUCCESS', 'FAILURE', 'ONGOING', 'NOT_APPLICABLE'
        )
    ),
    CONSTRAINT chk_treatment_none_time CHECK (
        (treatment_type = 'NONE' AND performed_at IS NULL)
        OR (treatment_type <> 'NONE' AND performed_at IS NOT NULL)
    )
);

CREATE INDEX idx_treatment_events_request_received
    ON treatment_events (transport_request_id, server_received_at);

CREATE TABLE current_patient_snapshots (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    latest_pre_ktas_assessment_id BIGINT NOT NULL,
    latest_consciousness_assessment_id BIGINT NOT NULL,
    latest_vital_sign_set_id BIGINT NOT NULL,
    last_clinical_update_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_current_snapshots_public_id UNIQUE (public_id),
    CONSTRAINT uk_current_snapshots_request UNIQUE (transport_request_id),
    CONSTRAINT fk_current_snapshots_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_current_snapshots_pre_ktas
        FOREIGN KEY (latest_pre_ktas_assessment_id) REFERENCES pre_ktas_assessments (id),
    CONSTRAINT fk_current_snapshots_consciousness
        FOREIGN KEY (latest_consciousness_assessment_id) REFERENCES consciousness_assessments (id),
    CONSTRAINT fk_current_snapshots_vital_set
        FOREIGN KEY (latest_vital_sign_set_id) REFERENCES vital_sign_sets (id)
);
