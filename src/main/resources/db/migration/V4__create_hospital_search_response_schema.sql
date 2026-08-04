ALTER TABLE organizations
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' AFTER type;

ALTER TABLE organizations
    ADD CONSTRAINT chk_organizations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'));

CREATE INDEX idx_organizations_type_status
    ON organizations (type, status);

CREATE TABLE hospital_dispatch_attempts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_radius_km INT NOT NULL DEFAULT 0,
    candidate_shortage BOOLEAN NOT NULL DEFAULT FALSE,
    next_expansion_at DATETIME(6) NULL,
    retry_idempotency_key VARCHAR(100) NULL,
    retry_fingerprint BINARY(32) NULL,
    started_at DATETIME(6) NOT NULL,
    ended_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_dispatch_attempts_public_id UNIQUE (public_id),
    CONSTRAINT uk_dispatch_attempts_request_number
        UNIQUE (transport_request_id, attempt_number),
    CONSTRAINT uk_dispatch_attempts_request_retry_key
        UNIQUE (transport_request_id, retry_idempotency_key),
    CONSTRAINT fk_dispatch_attempts_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT chk_dispatch_attempts_status CHECK (
        status IN ('SEARCHING', 'STOPPED_ON_ACCEPTANCE', 'EXHAUSTED')
    ),
    CONSTRAINT chk_dispatch_attempts_radius CHECK (
        current_radius_km BETWEEN 0 AND 100
        AND MOD(current_radius_km, 10) = 0
    ),
    CONSTRAINT chk_dispatch_attempts_retry_payload CHECK (
        (attempt_number = 1
            AND retry_idempotency_key IS NULL
            AND retry_fingerprint IS NULL)
        OR
        (attempt_number > 1
            AND retry_idempotency_key IS NOT NULL
            AND retry_fingerprint IS NOT NULL)
    )
);

CREATE INDEX idx_dispatch_attempts_due
    ON hospital_dispatch_attempts (status, next_expansion_at);

CREATE INDEX idx_dispatch_attempts_request_status
    ON hospital_dispatch_attempts (transport_request_id, status);

CREATE TABLE hospital_search_rounds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    dispatch_attempt_id BIGINT NOT NULL,
    radius_km INT NOT NULL,
    candidate_count INT NOT NULL,
    new_offer_count INT NOT NULL,
    evaluated_at DATETIME(6) NOT NULL,
    response_deadline_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_search_rounds_attempt_radius
        UNIQUE (dispatch_attempt_id, radius_km),
    CONSTRAINT fk_search_rounds_attempt
        FOREIGN KEY (dispatch_attempt_id) REFERENCES hospital_dispatch_attempts (id),
    CONSTRAINT chk_search_rounds_radius CHECK (
        radius_km BETWEEN 10 AND 100
        AND MOD(radius_km, 10) = 0
    ),
    CONSTRAINT chk_search_rounds_counts CHECK (
        candidate_count >= 0
        AND new_offer_count >= 0
        AND new_offer_count <= candidate_count
    )
);

CREATE INDEX idx_search_rounds_attempt_evaluated
    ON hospital_search_rounds (dispatch_attempt_id, evaluated_at);

CREATE TABLE hospital_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    dispatch_attempt_id BIGINT NOT NULL,
    search_round_id BIGINT NOT NULL,
    hospital_profile_id BIGINT NOT NULL,
    hospital_name_snapshot VARCHAR(100) NOT NULL,
    hospital_contact_snapshot VARCHAR(30) NOT NULL,
    hospital_latitude_snapshot DECIMAL(10, 7) NOT NULL,
    hospital_longitude_snapshot DECIMAL(10, 7) NOT NULL,
    straight_line_distance_m BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    route_estimate_status VARCHAR(20) NOT NULL,
    route_distance_m BIGINT NULL,
    eta_seconds BIGINT NULL,
    eta_calculated_at DATETIME(6) NULL,
    eta_attempt_count INT NOT NULL DEFAULT 0,
    eta_next_attempt_at DATETIME(6) NULL,
    response_idempotency_key VARCHAR(100) NULL,
    response_fingerprint BINARY(32) NULL,
    rejection_reason VARCHAR(50) NULL,
    rejection_detail VARCHAR(200) NULL,
    responded_by_account_id BIGINT NULL,
    responded_at DATETIME(6) NULL,
    offered_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_hospital_offers_public_id UNIQUE (public_id),
    CONSTRAINT uk_hospital_offers_attempt_hospital
        UNIQUE (dispatch_attempt_id, hospital_profile_id),
    CONSTRAINT fk_hospital_offers_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_hospital_offers_attempt
        FOREIGN KEY (dispatch_attempt_id) REFERENCES hospital_dispatch_attempts (id),
    CONSTRAINT fk_hospital_offers_round
        FOREIGN KEY (search_round_id) REFERENCES hospital_search_rounds (id),
    CONSTRAINT fk_hospital_offers_hospital
        FOREIGN KEY (hospital_profile_id) REFERENCES hospital_profiles (id),
    CONSTRAINT fk_hospital_offers_responder
        FOREIGN KEY (responded_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_hospital_offers_location CHECK (
        hospital_latitude_snapshot BETWEEN -90 AND 90
        AND hospital_longitude_snapshot BETWEEN -180 AND 180
    ),
    CONSTRAINT chk_hospital_offers_distance
        CHECK (straight_line_distance_m >= 0),
    CONSTRAINT chk_hospital_offers_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'NO_RESPONSE')),
    CONSTRAINT chk_hospital_offers_route_status
        CHECK (route_estimate_status IN ('CALCULATING', 'AVAILABLE', 'UNAVAILABLE')),
    CONSTRAINT chk_hospital_offers_route_payload CHECK (
        (route_estimate_status = 'CALCULATING'
            AND route_distance_m IS NULL
            AND eta_seconds IS NULL
            AND eta_calculated_at IS NULL)
        OR
        (route_estimate_status = 'AVAILABLE'
            AND route_distance_m IS NOT NULL
            AND route_distance_m >= 0
            AND eta_seconds IS NOT NULL
            AND eta_seconds >= 0
            AND eta_calculated_at IS NOT NULL)
        OR
        (route_estimate_status = 'UNAVAILABLE'
            AND route_distance_m IS NULL
            AND eta_seconds IS NULL
            AND eta_calculated_at IS NULL)
    ),
    CONSTRAINT chk_hospital_offers_response_key CHECK (
        (response_idempotency_key IS NULL AND response_fingerprint IS NULL)
        OR
        (response_idempotency_key IS NOT NULL AND response_fingerprint IS NOT NULL)
    ),
    CONSTRAINT chk_hospital_offers_response_payload CHECK (
        (status = 'PENDING'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NULL
            AND responded_at IS NULL
            AND closed_at IS NULL)
        OR
        (status = 'ACCEPTED'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NOT NULL
            AND responded_at IS NOT NULL
            AND closed_at IS NULL)
        OR
        (status = 'REJECTED'
            AND rejection_reason IS NOT NULL
            AND responded_by_account_id IS NOT NULL
            AND responded_at IS NOT NULL
            AND closed_at IS NOT NULL)
        OR
        (status = 'NO_RESPONSE'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NULL
            AND responded_at IS NULL
            AND closed_at IS NOT NULL)
    ),
    CONSTRAINT chk_hospital_offers_other_detail CHECK (
        rejection_reason <> 'OTHER'
        OR (rejection_detail IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_detail)) > 0)
    )
);

CREATE INDEX idx_hospital_offers_hospital_status_offered
    ON hospital_offers (hospital_profile_id, status, offered_at);

CREATE INDEX idx_hospital_offers_request_offered
    ON hospital_offers (transport_request_id, offered_at);

CREATE INDEX idx_hospital_offers_attempt_status
    ON hospital_offers (dispatch_attempt_id, status);

CREATE INDEX idx_hospital_offers_eta_due
    ON hospital_offers (route_estimate_status, eta_next_attempt_at);

CREATE TABLE hospital_offer_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    hospital_offer_id BIGINT NOT NULL,
    event_type VARCHAR(30) NOT NULL,
    actor_account_id BIGINT NULL,
    actor_organization_id BIGINT NULL,
    rejection_reason VARCHAR(50) NULL,
    rejection_detail VARCHAR(200) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_hospital_offer_events_public_id UNIQUE (public_id),
    CONSTRAINT fk_hospital_offer_events_offer
        FOREIGN KEY (hospital_offer_id) REFERENCES hospital_offers (id),
    CONSTRAINT fk_hospital_offer_events_actor_account
        FOREIGN KEY (actor_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_hospital_offer_events_actor_org
        FOREIGN KEY (actor_organization_id) REFERENCES organizations (id),
    CONSTRAINT chk_hospital_offer_events_type
        CHECK (event_type IN ('OFFERED', 'ACCEPTED', 'REJECTED', 'NO_RESPONSE')),
    CONSTRAINT chk_hospital_offer_events_payload CHECK (
        (event_type = 'OFFERED'
            AND actor_account_id IS NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL)
        OR
        (event_type = 'ACCEPTED'
            AND actor_account_id IS NOT NULL
            AND actor_organization_id IS NOT NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL)
        OR
        (event_type = 'REJECTED'
            AND actor_account_id IS NOT NULL
            AND actor_organization_id IS NOT NULL
            AND rejection_reason IS NOT NULL)
        OR
        (event_type = 'NO_RESPONSE'
            AND actor_account_id IS NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL)
    ),
    CONSTRAINT chk_hospital_offer_events_other_detail CHECK (
        rejection_reason <> 'OTHER'
        OR (rejection_detail IS NOT NULL AND CHAR_LENGTH(TRIM(rejection_detail)) > 0)
    )
);

CREATE INDEX idx_hospital_offer_events_offer_occurred
    ON hospital_offer_events (hospital_offer_id, occurred_at);

CREATE TABLE realtime_outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    audience_type VARCHAR(20) NOT NULL,
    audience_public_id CHAR(36) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_public_id CHAR(36) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    publish_attempt_count INT NOT NULL DEFAULT 0,
    next_publish_attempt_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_realtime_outbox_public_id UNIQUE (public_id),
    CONSTRAINT chk_realtime_outbox_event_type CHECK (
        event_type IN (
            'TRANSPORT_REQUEST_RECEIVED',
            'HOSPITAL_OFFER_ACCEPTED',
            'HOSPITAL_OFFER_REJECTED',
            'HOSPITAL_OFFER_NO_RESPONSE',
            'HOSPITAL_SEARCH_EXHAUSTED',
            'HOSPITAL_SEARCH_RETRY_STARTED',
            'ETA_UPDATED'
        )
    ),
    CONSTRAINT chk_realtime_outbox_audience
        CHECK (audience_type IN ('ACCOUNT', 'ORGANIZATION')),
    CONSTRAINT chk_realtime_outbox_attempts
        CHECK (publish_attempt_count >= 0)
);

CREATE INDEX idx_realtime_outbox_due
    ON realtime_outbox_events (published_at, next_publish_attempt_at, occurred_at);

CREATE INDEX idx_realtime_outbox_audience
    ON realtime_outbox_events (audience_type, audience_public_id, occurred_at);
