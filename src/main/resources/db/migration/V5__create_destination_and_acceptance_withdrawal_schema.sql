ALTER TABLE hospital_dispatch_attempts
    ADD COLUMN trigger_type VARCHAR(30) NULL AFTER attempt_number;

ALTER TABLE hospital_dispatch_attempts
    ADD COLUMN search_origin_latitude DECIMAL(10, 7) NULL AFTER retry_fingerprint;

ALTER TABLE hospital_dispatch_attempts
    ADD COLUMN search_origin_longitude DECIMAL(10, 7) NULL AFTER search_origin_latitude;

UPDATE hospital_dispatch_attempts attempt
SET trigger_type = CASE
        WHEN attempt_number = 1 THEN 'INITIAL'
        ELSE 'MANUAL_RETRY'
    END,
    search_origin_latitude = (
        SELECT request.origin_latitude
        FROM transport_requests request
        WHERE request.id = attempt.transport_request_id
    ),
    search_origin_longitude = (
        SELECT request.origin_longitude
        FROM transport_requests request
        WHERE request.id = attempt.transport_request_id
    );

ALTER TABLE hospital_dispatch_attempts
    MODIFY COLUMN trigger_type VARCHAR(30) NOT NULL;

ALTER TABLE hospital_dispatch_attempts
    MODIFY COLUMN search_origin_latitude DECIMAL(10, 7) NOT NULL;

ALTER TABLE hospital_dispatch_attempts
    MODIFY COLUMN search_origin_longitude DECIMAL(10, 7) NOT NULL;

ALTER TABLE hospital_dispatch_attempts
    DROP CONSTRAINT chk_dispatch_attempts_status;

ALTER TABLE hospital_dispatch_attempts
    ADD CONSTRAINT chk_dispatch_attempts_status CHECK (
        status IN (
            'SEARCHING',
            'STOPPED_ON_ACCEPTANCE',
            'STOPPED_ON_DESTINATION',
            'EXHAUSTED'
        )
    );

ALTER TABLE hospital_dispatch_attempts
    DROP CONSTRAINT chk_dispatch_attempts_retry_payload;

ALTER TABLE hospital_dispatch_attempts
    ADD CONSTRAINT chk_dispatch_attempts_trigger
        CHECK (trigger_type IN ('INITIAL', 'MANUAL_RETRY', 'ACCEPTANCE_WITHDRAWAL'));

ALTER TABLE hospital_dispatch_attempts
    ADD CONSTRAINT chk_dispatch_attempts_origin CHECK (
        search_origin_latitude BETWEEN -90 AND 90
        AND search_origin_longitude BETWEEN -180 AND 180
    );

ALTER TABLE hospital_dispatch_attempts
    ADD CONSTRAINT chk_dispatch_attempts_retry_payload CHECK (
        (trigger_type = 'INITIAL'
            AND attempt_number = 1
            AND retry_idempotency_key IS NULL
            AND retry_fingerprint IS NULL)
        OR
        (trigger_type = 'MANUAL_RETRY'
            AND attempt_number > 1
            AND retry_idempotency_key IS NOT NULL
            AND retry_fingerprint IS NOT NULL)
        OR
        (trigger_type = 'ACCEPTANCE_WITHDRAWAL'
            AND attempt_number > 1
            AND retry_idempotency_key IS NULL
            AND retry_fingerprint IS NULL)
    );

CREATE INDEX idx_dispatch_attempts_request_trigger_status
    ON hospital_dispatch_attempts (transport_request_id, trigger_type, status);

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_reason VARCHAR(50) NULL AFTER rejection_detail;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_detail VARCHAR(200) NULL AFTER withdrawal_reason;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_idempotency_key VARCHAR(100) NULL AFTER withdrawal_detail;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_fingerprint BINARY(32) NULL AFTER withdrawal_idempotency_key;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawn_by_account_id BIGINT NULL AFTER responded_by_account_id;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawn_at DATETIME(6) NULL AFTER responded_at;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_resulting_request_status VARCHAR(30) NULL AFTER withdrawn_at;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_resulting_destination_offer_id BIGINT NULL
        AFTER withdrawal_resulting_request_status;

ALTER TABLE hospital_offers
    ADD COLUMN withdrawal_search_restarted BOOLEAN NULL
        AFTER withdrawal_resulting_destination_offer_id;

ALTER TABLE hospital_offers
    ADD CONSTRAINT fk_hospital_offers_withdrawer
        FOREIGN KEY (withdrawn_by_account_id) REFERENCES user_accounts (id);

ALTER TABLE hospital_offers
    ADD CONSTRAINT fk_hospital_offers_withdrawal_result_destination
        FOREIGN KEY (withdrawal_resulting_destination_offer_id) REFERENCES hospital_offers (id);

ALTER TABLE hospital_offers
    DROP CONSTRAINT chk_hospital_offers_status;

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'NO_RESPONSE', 'ACCEPTANCE_WITHDRAWN')
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
            AND closed_at IS NULL
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
            AND closed_at IS NULL
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

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_withdrawal_key CHECK (
        (withdrawal_idempotency_key IS NULL AND withdrawal_fingerprint IS NULL)
        OR
        (withdrawal_idempotency_key IS NOT NULL AND withdrawal_fingerprint IS NOT NULL)
    );

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_withdrawal_reason CHECK (
        withdrawal_reason IS NULL
        OR withdrawal_reason IN (
            'BED_SHORTAGE',
            'OPERATING_ROOM_SHORTAGE',
            'SPECIALIST_UNAVAILABLE',
            'EQUIPMENT_UNAVAILABLE',
            'OTHER'
        )
    );

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_withdrawal_other_detail CHECK (
        withdrawal_reason <> 'OTHER'
        OR (withdrawal_detail IS NOT NULL AND CHAR_LENGTH(TRIM(withdrawal_detail)) > 0)
    );

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_withdrawal_result_status CHECK (
        withdrawal_resulting_request_status IS NULL
        OR withdrawal_resulting_request_status IN ('SEARCHING', 'ACCEPTED_AVAILABLE', 'EN_ROUTE')
    );

CREATE INDEX idx_hospital_offers_request_status_hospital
    ON hospital_offers (transport_request_id, status, hospital_profile_id);

ALTER TABLE hospital_offer_events
    ADD COLUMN withdrawal_reason VARCHAR(50) NULL AFTER rejection_detail;

ALTER TABLE hospital_offer_events
    ADD COLUMN withdrawal_detail VARCHAR(200) NULL AFTER withdrawal_reason;

ALTER TABLE hospital_offer_events
    DROP CONSTRAINT chk_hospital_offer_events_type;

ALTER TABLE hospital_offer_events
    ADD CONSTRAINT chk_hospital_offer_events_type CHECK (
        event_type IN ('OFFERED', 'ACCEPTED', 'REJECTED', 'NO_RESPONSE', 'ACCEPTANCE_WITHDRAWN')
    );

ALTER TABLE hospital_offer_events
    DROP CONSTRAINT chk_hospital_offer_events_payload;

ALTER TABLE hospital_offer_events
    ADD CONSTRAINT chk_hospital_offer_events_payload CHECK (
        (event_type = 'OFFERED'
            AND actor_account_id IS NULL
            AND actor_organization_id IS NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL)
        OR
        (event_type = 'ACCEPTED'
            AND actor_account_id IS NOT NULL
            AND actor_organization_id IS NOT NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL)
        OR
        (event_type = 'REJECTED'
            AND actor_account_id IS NOT NULL
            AND actor_organization_id IS NOT NULL
            AND rejection_reason IS NOT NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL)
        OR
        (event_type = 'NO_RESPONSE'
            AND actor_account_id IS NULL
            AND actor_organization_id IS NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND withdrawal_reason IS NULL
            AND withdrawal_detail IS NULL)
        OR
        (event_type = 'ACCEPTANCE_WITHDRAWN'
            AND actor_account_id IS NOT NULL
            AND actor_organization_id IS NOT NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND withdrawal_reason IS NOT NULL)
    );

ALTER TABLE hospital_offer_events
    ADD CONSTRAINT chk_hospital_offer_events_withdrawal_reason CHECK (
        withdrawal_reason IS NULL
        OR withdrawal_reason IN (
            'BED_SHORTAGE',
            'OPERATING_ROOM_SHORTAGE',
            'SPECIALIST_UNAVAILABLE',
            'EQUIPMENT_UNAVAILABLE',
            'OTHER'
        )
    );

ALTER TABLE hospital_offer_events
    ADD CONSTRAINT chk_hospital_offer_events_withdrawal_other_detail CHECK (
        withdrawal_reason <> 'OTHER'
        OR (withdrawal_detail IS NOT NULL AND CHAR_LENGTH(TRIM(withdrawal_detail)) > 0)
    );

ALTER TABLE transport_requests
    ADD COLUMN current_destination_offer_id BIGINT NULL AFTER status;

ALTER TABLE transport_requests
    ADD CONSTRAINT fk_transport_requests_current_destination
        FOREIGN KEY (current_destination_offer_id) REFERENCES hospital_offers (id);

CREATE INDEX idx_transport_requests_current_destination
    ON transport_requests (current_destination_offer_id);

CREATE TABLE transport_destination_commands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    transport_request_id BIGINT NOT NULL,
    previous_destination_offer_id BIGINT NULL,
    destination_offer_id BIGINT NOT NULL,
    result_type VARCHAR(20) NOT NULL,
    actor_account_id BIGINT NOT NULL,
    actor_organization_id BIGINT NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_fingerprint BINARY(32) NOT NULL,
    resulting_request_status VARCHAR(30) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_destination_commands_public_id UNIQUE (public_id),
    CONSTRAINT uk_destination_commands_request_key
        UNIQUE (transport_request_id, idempotency_key),
    CONSTRAINT fk_destination_commands_request
        FOREIGN KEY (transport_request_id) REFERENCES transport_requests (id),
    CONSTRAINT fk_destination_commands_previous_offer
        FOREIGN KEY (previous_destination_offer_id) REFERENCES hospital_offers (id),
    CONSTRAINT fk_destination_commands_offer
        FOREIGN KEY (destination_offer_id) REFERENCES hospital_offers (id),
    CONSTRAINT fk_destination_commands_actor_account
        FOREIGN KEY (actor_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_destination_commands_actor_org
        FOREIGN KEY (actor_organization_id) REFERENCES organizations (id),
    CONSTRAINT chk_destination_commands_result
        CHECK (result_type IN ('SELECTED', 'CHANGED', 'UNCHANGED')),
    CONSTRAINT chk_destination_commands_request_status
        CHECK (resulting_request_status = 'EN_ROUTE'),
    CONSTRAINT chk_destination_commands_transition CHECK (
        (result_type = 'SELECTED' AND previous_destination_offer_id IS NULL)
        OR
        (result_type = 'CHANGED'
            AND previous_destination_offer_id IS NOT NULL
            AND previous_destination_offer_id <> destination_offer_id)
        OR
        (result_type = 'UNCHANGED'
            AND previous_destination_offer_id = destination_offer_id)
    )
);

CREATE INDEX idx_destination_commands_request_occurred
    ON transport_destination_commands (transport_request_id, occurred_at);

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
            'HOSPITAL_ACCEPTANCE_WITHDRAWN'
        )
    );
