ALTER TABLE hospital_profiles
    ADD COLUMN detail_address VARCHAR(200) NULL AFTER address;

ALTER TABLE hospital_offers
    ADD COLUMN hospital_address_snapshot VARCHAR(255) NULL AFTER hospital_contact_snapshot;

ALTER TABLE hospital_offers
    ADD COLUMN hospital_detail_address_snapshot VARCHAR(200) NULL AFTER hospital_address_snapshot;

UPDATE hospital_offers
SET hospital_address_snapshot = (
    SELECT profile.address
    FROM hospital_profiles profile
    WHERE profile.id = hospital_offers.hospital_profile_id
);

ALTER TABLE hospital_offers
    MODIFY COLUMN hospital_address_snapshot VARCHAR(255) NOT NULL;
