ALTER TABLE transport_requests
    ADD COLUMN cancellation_reason VARCHAR(40) NULL AFTER current_destination_offer_id;

ALTER TABLE transport_requests
    ADD COLUMN cancellation_detail VARCHAR(200) NULL AFTER cancellation_reason;

ALTER TABLE transport_requests
    ADD COLUMN cancelled_by_account_id BIGINT NULL AFTER cancellation_detail;

ALTER TABLE transport_requests
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER cancelled_by_account_id;

ALTER TABLE transport_requests
    ADD COLUMN handoff_requested_by_account_id BIGINT NULL AFTER cancelled_at;

ALTER TABLE transport_requests
    ADD COLUMN handoff_requested_at DATETIME(6) NULL AFTER handoff_requested_by_account_id;

ALTER TABLE transport_requests
    ADD COLUMN handoff_confirmed_by_account_id BIGINT NULL AFTER handoff_requested_at;

ALTER TABLE transport_requests
    ADD COLUMN completed_at DATETIME(6) NULL AFTER handoff_confirmed_by_account_id;

ALTER TABLE transport_requests
    ADD CONSTRAINT fk_transport_requests_cancelled_by
        FOREIGN KEY (cancelled_by_account_id) REFERENCES user_accounts (id);

ALTER TABLE transport_requests
    ADD CONSTRAINT fk_transport_requests_handoff_requested_by
        FOREIGN KEY (handoff_requested_by_account_id) REFERENCES user_accounts (id);

ALTER TABLE transport_requests
    ADD CONSTRAINT fk_transport_requests_handoff_confirmed_by
        FOREIGN KEY (handoff_confirmed_by_account_id) REFERENCES user_accounts (id);

ALTER TABLE transport_requests
    ADD CONSTRAINT chk_transport_requests_cancellation_reason CHECK (
        cancellation_reason IS NULL
        OR cancellation_reason IN (
            'PATIENT_REFUSED_TRANSPORT',
            'GUARDIAN_SELF_TRANSPORT',
            'SCENE_RESOLVED',
            'OTHER'
        )
    );

ALTER TABLE transport_requests
    ADD CONSTRAINT chk_transport_requests_cancellation_detail CHECK (
        cancellation_reason IS NULL
        OR (cancellation_reason = 'OTHER'
            AND cancellation_detail IS NOT NULL
            AND CHAR_LENGTH(TRIM(cancellation_detail)) BETWEEN 1 AND 200)
        OR (cancellation_reason <> 'OTHER' AND cancellation_detail IS NULL)
    );

CREATE INDEX idx_transport_requests_owner_lifecycle
    ON transport_requests (
        owner_account_id,
        status,
        handoff_requested_at,
        completed_at,
        cancelled_at
    );

CREATE TABLE transport_lifecycle_commands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    command_type VARCHAR(30) NOT NULL,
    actor_account_id BIGINT NOT NULL,
    actor_organization_id BIGINT NOT NULL,
    destination_offer_id BIGINT NULL,
    cancellation_reason VARCHAR(40) NULL,
    cancellation_detail VARCHAR(200) NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    resulting_request_status VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_transport_lifecycle_commands_public_id UNIQUE (public_id),
    CONSTRAINT uk_transport_lifecycle_commands_request_key
        UNIQUE (transport_request_id, idempotency_key),
    CONSTRAINT fk_transport_lifecycle_commands_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_transport_lifecycle_commands_actor_account
        FOREIGN KEY (actor_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_transport_lifecycle_commands_actor_org
        FOREIGN KEY (actor_organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_transport_lifecycle_commands_destination
        FOREIGN KEY (destination_offer_id) REFERENCES hospital_offers (id),
    CONSTRAINT chk_transport_lifecycle_commands_type CHECK (
        command_type IN ('CANCEL', 'HANDOFF_REQUEST', 'HANDOFF_CONFIRM')
    ),
    CONSTRAINT chk_transport_lifecycle_commands_result CHECK (
        (command_type = 'CANCEL' AND resulting_request_status = 'CANCELLED')
        OR (command_type = 'HANDOFF_REQUEST' AND resulting_request_status = 'HANDOFF_REQUESTED')
        OR (command_type = 'HANDOFF_CONFIRM' AND resulting_request_status = 'COMPLETED')
    ),
    CONSTRAINT chk_transport_lifecycle_commands_payload CHECK (
        (command_type = 'CANCEL'
            AND cancellation_reason IS NOT NULL)
        OR
        (command_type IN ('HANDOFF_REQUEST', 'HANDOFF_CONFIRM')
            AND destination_offer_id IS NOT NULL
            AND cancellation_reason IS NULL
            AND cancellation_detail IS NULL)
    ),
    CONSTRAINT chk_transport_lifecycle_commands_reason CHECK (
        cancellation_reason IS NULL
        OR cancellation_reason IN (
            'PATIENT_REFUSED_TRANSPORT',
            'GUARDIAN_SELF_TRANSPORT',
            'SCENE_RESOLVED',
            'OTHER'
        )
    ),
    CONSTRAINT chk_transport_lifecycle_commands_detail CHECK (
        cancellation_reason IS NULL
        OR (cancellation_reason = 'OTHER'
            AND cancellation_detail IS NOT NULL
            AND CHAR_LENGTH(TRIM(cancellation_detail)) BETWEEN 1 AND 200)
        OR (cancellation_reason <> 'OTHER' AND cancellation_detail IS NULL)
    )
);

CREATE INDEX idx_transport_lifecycle_commands_request_occurred
    ON transport_lifecycle_commands (transport_request_id, occurred_at);

ALTER TABLE hospital_dispatch_attempts
    DROP CONSTRAINT chk_dispatch_attempts_status;

ALTER TABLE hospital_dispatch_attempts
    ADD CONSTRAINT chk_dispatch_attempts_status CHECK (
        status IN (
            'SEARCHING',
            'STOPPED_ON_ACCEPTANCE',
            'STOPPED_ON_DESTINATION',
            'STOPPED_ON_CANCELLATION',
            'EXHAUSTED'
        )
    );

ALTER TABLE hospital_offers
    DROP CONSTRAINT chk_hospital_offers_response_payload;

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_response_payload CHECK (
        (status = 'PENDING'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NULL
            AND responded_at IS NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL
            AND withdrawal_idempotency_key IS NULL
            AND withdrawal_fingerprint IS NULL
            AND withdrawn_by_account_id IS NULL
            AND withdrawn_at IS NULL
            AND withdrawal_resulting_request_status IS NULL
            AND withdrawal_resulting_destination_offer_id IS NULL
            AND withdrawal_search_restarted IS NULL)
        OR
        (status = 'ACCEPTED'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NOT NULL
            AND responded_at IS NOT NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL
            AND withdrawal_idempotency_key IS NULL
            AND withdrawal_fingerprint IS NULL
            AND withdrawn_by_account_id IS NULL
            AND withdrawn_at IS NULL
            AND withdrawal_resulting_request_status IS NULL
            AND withdrawal_resulting_destination_offer_id IS NULL
            AND withdrawal_search_restarted IS NULL)
        OR
        (status = 'REJECTED'
            AND rejection_reason IS NOT NULL
            AND responded_by_account_id IS NOT NULL
            AND responded_at IS NOT NULL
            AND closed_at IS NOT NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL
            AND withdrawal_idempotency_key IS NULL
            AND withdrawal_fingerprint IS NULL
            AND withdrawn_by_account_id IS NULL
            AND withdrawn_at IS NULL
            AND withdrawal_resulting_request_status IS NULL
            AND withdrawal_resulting_destination_offer_id IS NULL
            AND withdrawal_search_restarted IS NULL)
        OR
        (status = 'NO_RESPONSE'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NULL
            AND responded_at IS NULL
            AND closed_at IS NOT NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL
            AND withdrawal_idempotency_key IS NULL
            AND withdrawal_fingerprint IS NULL
            AND withdrawn_by_account_id IS NULL
            AND withdrawn_at IS NULL
            AND withdrawal_resulting_request_status IS NULL
            AND withdrawal_resulting_destination_offer_id IS NULL
            AND withdrawal_search_restarted IS NULL)
        OR
        (status = 'ACCEPTANCE_WITHDRAWN'
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND responded_by_account_id IS NOT NULL
            AND responded_at IS NOT NULL
            AND closed_at IS NOT NULL
            AND withdrawal_reason IS NOT NULL
            AND withdrawal_idempotency_key IS NOT NULL
            AND withdrawal_fingerprint IS NOT NULL
            AND withdrawn_by_account_id IS NOT NULL
            AND withdrawn_at IS NOT NULL
            AND withdrawal_resulting_request_status IS NOT NULL
            AND withdrawal_search_restarted IS NOT NULL
            AND closed_at = withdrawn_at)
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
            'AMBULANCE_LOCATION_UPDATED',
            'TRANSPORT_CANCELLED',
            'HANDOFF_REQUESTED',
            'HANDOFF_COMPLETED'
        )
    );
