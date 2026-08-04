CREATE TABLE transport_update_commands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    command_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    result_record_public_id CHAR(36) NULL,
    result_clinical_at DATETIME(6) NULL,
    snapshot_updated BOOLEAN NULL,
    location_replaced BOOLEAN NULL,
    server_received_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transport_update_commands_public_id UNIQUE (public_id),
    CONSTRAINT uk_transport_update_commands_request_key
        UNIQUE (transport_request_id, idempotency_key),
    CONSTRAINT fk_transport_update_commands_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT chk_transport_update_commands_type CHECK (
        command_type IN ('VITAL_SIGNS', 'CONSCIOUSNESS', 'PRE_KTAS', 'TREATMENT', 'LOCATION')
    ),
    CONSTRAINT chk_transport_update_commands_result CHECK (
        (command_type = 'LOCATION'
            AND result_record_public_id IS NOT NULL
            AND result_clinical_at IS NULL
            AND snapshot_updated IS NULL
            AND location_replaced IS NOT NULL)
        OR
        (command_type <> 'LOCATION'
            AND result_record_public_id IS NOT NULL
            AND result_clinical_at IS NOT NULL
            AND snapshot_updated IS NOT NULL
            AND location_replaced IS NULL)
    )
);

CREATE INDEX idx_transport_update_commands_request_received
    ON transport_update_commands (transport_request_id, server_received_at);

CREATE TABLE transport_current_locations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    captured_at DATETIME(6) NOT NULL,
    last_received_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_transport_current_locations_public_id UNIQUE (public_id),
    CONSTRAINT uk_transport_current_locations_request UNIQUE (transport_request_id),
    CONSTRAINT fk_transport_current_locations_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT chk_transport_current_locations_coordinates CHECK (
        latitude BETWEEN -90 AND 90
        AND longitude BETWEEN -180 AND 180
    )
);

ALTER TABLE hospital_offers
    ADD COLUMN route_estimate_generation BIGINT NOT NULL DEFAULT 0 AFTER eta_next_attempt_at;

ALTER TABLE hospital_offers
    ADD COLUMN last_success_route_distance_m BIGINT NULL AFTER route_estimate_generation;

ALTER TABLE hospital_offers
    ADD COLUMN last_success_eta_seconds BIGINT NULL AFTER last_success_route_distance_m;

ALTER TABLE hospital_offers
    ADD COLUMN last_success_eta_calculated_at DATETIME(6) NULL AFTER last_success_eta_seconds;

UPDATE hospital_offers
SET last_success_route_distance_m = route_distance_m,
    last_success_eta_seconds = eta_seconds,
    last_success_eta_calculated_at = eta_calculated_at
WHERE route_estimate_status = 'AVAILABLE';

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_route_generation
        CHECK (route_estimate_generation >= 0);

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_last_success_route CHECK (
        (last_success_route_distance_m IS NULL
            AND last_success_eta_seconds IS NULL
            AND last_success_eta_calculated_at IS NULL)
        OR
        (last_success_route_distance_m IS NOT NULL
            AND last_success_route_distance_m >= 0
            AND last_success_eta_seconds IS NOT NULL
            AND last_success_eta_seconds >= 0
            AND last_success_eta_calculated_at IS NOT NULL)
    );

ALTER TABLE realtime_outbox_events
    DROP CONSTRAINT chk_realtime_outbox_event_type;

ALTER TABLE realtime_outbox_events
    ADD CONSTRAINT chk_realtime_outbox_event_type CHECK (
        event_type IN (
            'TRANSPORT_REQUEST_RECEIVED',
            'HOSPITAL_OFFER_ACCEPTED',
            'HOSPITAL_OFFER_REJECTED',
            'HOSPITAL_OFFER_NO_RESPONSE',
            'HOSPITAL_SEARCH_EXHAUSTED',
            'HOSPITAL_SEARCH_RETRY_STARTED',
            'ETA_UPDATED',
            'DESTINATION_SELECTED',
            'DESTINATION_CHANGED',
            'HOSPITAL_ACCEPTANCE_WITHDRAWN',
            'VITAL_SIGNS_ADDED',
            'CONSCIOUSNESS_CHANGED',
            'PRE_KTAS_CHANGED',
            'TREATMENT_ADDED',
            'AMBULANCE_LOCATION_UPDATED'
        )
    );
