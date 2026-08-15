ALTER TABLE hospital_offers
    ADD COLUMN last_requested_at DATETIME(6) NULL AFTER offered_at;

ALTER TABLE hospital_offers
    ADD COLUMN renotification_count INT NOT NULL DEFAULT 0 AFTER last_requested_at;

ALTER TABLE hospital_offers
    ADD COLUMN last_requested_attempt_id BIGINT NULL AFTER renotification_count;

UPDATE hospital_offers
SET last_requested_at = offered_at,
    last_requested_attempt_id = dispatch_attempt_id;

ALTER TABLE hospital_offers
    MODIFY COLUMN last_requested_at DATETIME(6) NOT NULL;

ALTER TABLE hospital_offers
    MODIFY COLUMN last_requested_attempt_id BIGINT NOT NULL;

CREATE INDEX idx_hospital_offers_last_requested_attempt
    ON hospital_offers (last_requested_attempt_id);

ALTER TABLE hospital_offers
    ADD CONSTRAINT fk_hospital_offers_last_requested_attempt
        FOREIGN KEY (last_requested_attempt_id) REFERENCES hospital_dispatch_attempts (id);

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_renotification CHECK (
        (renotification_count = 0
            AND last_requested_at = offered_at
            AND last_requested_attempt_id = dispatch_attempt_id)
        OR
        (renotification_count > 0
            AND last_requested_at >= offered_at)
    );

ALTER TABLE hospital_offer_events
    DROP CONSTRAINT chk_hospital_offer_events_type;

ALTER TABLE hospital_offer_events
    ADD CONSTRAINT chk_hospital_offer_events_type CHECK (
        event_type IN (
            'OFFERED',
            'RENOTIFIED',
            'ACCEPTED',
            'REJECTED',
            'NO_RESPONSE',
            'ACCEPTANCE_WITHDRAWN'
        )
    );

ALTER TABLE hospital_offer_events
    DROP CONSTRAINT chk_hospital_offer_events_payload;

ALTER TABLE hospital_offer_events
    ADD CONSTRAINT chk_hospital_offer_events_payload CHECK (
        (event_type IN ('OFFERED', 'RENOTIFIED', 'NO_RESPONSE')
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
        (event_type = 'ACCEPTANCE_WITHDRAWN'
            AND actor_account_id IS NOT NULL
            AND actor_organization_id IS NOT NULL
            AND rejection_reason IS NULL
            AND rejection_detail IS NULL
            AND withdrawal_reason IS NOT NULL)
    );
