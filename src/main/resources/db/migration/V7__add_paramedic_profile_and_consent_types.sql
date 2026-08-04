ALTER TABLE paramedic_profiles
    ADD COLUMN display_name VARCHAR(50) NULL;

UPDATE paramedic_profiles profile
SET display_name = (
    SELECT account.login_id
    FROM user_accounts account
    WHERE account.id = profile.account_id
);

ALTER TABLE paramedic_profiles
    MODIFY COLUMN display_name VARCHAR(50) NOT NULL;

ALTER TABLE contact_sharing_consents
    ADD COLUMN consent_type VARCHAR(40) NULL;

UPDATE contact_sharing_consents
SET consent_type = 'CONTACT_COLLECTION_AND_PROVISION';

ALTER TABLE contact_sharing_consents
    MODIFY COLUMN consent_type VARCHAR(40) NOT NULL;

ALTER TABLE contact_sharing_consents
    DROP INDEX uk_contact_consents_account_version;

ALTER TABLE contact_sharing_consents
    ADD CONSTRAINT uk_contact_consents_account_type_version
        UNIQUE (account_id, consent_type, policy_version);

ALTER TABLE contact_sharing_consents
    ADD CONSTRAINT chk_contact_consents_type CHECK (
        consent_type IN (
            'CONTACT_COLLECTION_AND_PROVISION',
            'CONTACT_COLLECTION_USE',
            'HOSPITAL_PROVISION'
        )
    );
