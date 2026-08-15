ALTER TABLE hospital_offers
    ADD COLUMN clinical_visibility_cutoff_at DATETIME(6) NULL AFTER offered_at;

ALTER TABLE hospital_offers
    ADD COLUMN frozen_last_clinical_update_at DATETIME(6) NULL
        AFTER clinical_visibility_cutoff_at;

UPDATE hospital_offers offer
SET clinical_visibility_cutoff_at = offered_at,
    frozen_last_clinical_update_at = (
        SELECT CASE
            WHEN snapshot.last_clinical_update_at <= offer.offered_at
                THEN snapshot.last_clinical_update_at
            ELSE offer.offered_at
        END
        FROM current_patient_snapshots snapshot
        WHERE snapshot.transport_request_id = offer.transport_request_id
    )
WHERE offer.status IN ('PENDING', 'ACCEPTED')
  AND offer.closed_at IS NULL
  AND EXISTS (
      SELECT 1
      FROM transport_requests request
      WHERE request.id = offer.transport_request_id
        AND request.current_destination_offer_id IS NOT NULL
        AND request.current_destination_offer_id <> offer.id
        AND request.status NOT IN ('COMPLETED', 'CANCELLED')
  );

ALTER TABLE hospital_offers
    ADD CONSTRAINT chk_hospital_offers_clinical_visibility CHECK (
        (clinical_visibility_cutoff_at IS NULL
            AND frozen_last_clinical_update_at IS NULL)
        OR
        (clinical_visibility_cutoff_at IS NOT NULL
            AND frozen_last_clinical_update_at IS NOT NULL)
    );
