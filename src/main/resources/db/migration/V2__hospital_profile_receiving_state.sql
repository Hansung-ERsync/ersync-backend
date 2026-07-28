CREATE TABLE hospital_profiles (
    organization_id VARCHAR(36) NOT NULL,
    er_address VARCHAR(255) NOT NULL,
    latitude DECIMAL(9, 6) NOT NULL,
    longitude DECIMAL(9, 6) NOT NULL,
    er_contact VARCHAR(40) NOT NULL,
    receiving_status VARCHAR(16) NOT NULL,
    location_verified_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (organization_id),
    CONSTRAINT fk_hospital_profiles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_hospital_profiles_receiving_status
    ON hospital_profiles (receiving_status);

CREATE INDEX idx_hospital_profiles_latitude_longitude
    ON hospital_profiles (latitude, longitude);
