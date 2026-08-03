ALTER TABLE current_patient_snapshots
    ADD COLUMN patient_demographics_id BIGINT NULL;

ALTER TABLE current_patient_snapshots
    ADD COLUMN incident_assessment_id BIGINT NULL;

ALTER TABLE current_patient_snapshots
    ADD COLUMN assessment_protocol_version VARCHAR(50) NULL;

UPDATE current_patient_snapshots snapshot
SET patient_demographics_id = (
        SELECT demographics.id
        FROM patient_demographics demographics
        WHERE demographics.transport_request_id = snapshot.transport_request_id
    ),
    incident_assessment_id = (
        SELECT incident.id
        FROM incident_assessments incident
        WHERE incident.transport_request_id = snapshot.transport_request_id
    ),
    assessment_protocol_version = (
        SELECT transport.assessment_protocol_version
        FROM transport_requests transport
        WHERE transport.id = snapshot.transport_request_id
    );

ALTER TABLE current_patient_snapshots
    ADD CONSTRAINT fk_current_snapshots_demographics
        FOREIGN KEY (patient_demographics_id) REFERENCES patient_demographics (id);

ALTER TABLE current_patient_snapshots
    ADD CONSTRAINT fk_current_snapshots_incident
        FOREIGN KEY (incident_assessment_id) REFERENCES incident_assessments (id);

ALTER TABLE current_patient_snapshots
    ADD CONSTRAINT chk_current_snapshots_demographics_required
        CHECK (patient_demographics_id IS NOT NULL);

ALTER TABLE current_patient_snapshots
    ADD CONSTRAINT chk_current_snapshots_incident_required
        CHECK (incident_assessment_id IS NOT NULL);

ALTER TABLE current_patient_snapshots
    ADD CONSTRAINT chk_current_snapshots_protocol_required
        CHECK (assessment_protocol_version IS NOT NULL);

CREATE TABLE current_patient_snapshot_treatments (
    snapshot_id BIGINT NOT NULL,
    treatment_event_id BIGINT NOT NULL,
    PRIMARY KEY (snapshot_id, treatment_event_id),
    CONSTRAINT uk_current_snapshot_treatments_event UNIQUE (treatment_event_id),
    CONSTRAINT fk_current_snapshot_treatments_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES current_patient_snapshots (id),
    CONSTRAINT fk_current_snapshot_treatments_event
        FOREIGN KEY (treatment_event_id) REFERENCES treatment_events (id)
);

INSERT INTO current_patient_snapshot_treatments (snapshot_id, treatment_event_id)
SELECT snapshot.id, treatment.id
FROM current_patient_snapshots snapshot
JOIN treatment_events treatment
  ON treatment.transport_request_id = snapshot.transport_request_id;
