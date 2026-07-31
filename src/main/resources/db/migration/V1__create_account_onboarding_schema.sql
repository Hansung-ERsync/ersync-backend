CREATE TABLE organizations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_organizations_public_id UNIQUE (public_id),
    CONSTRAINT chk_organizations_type CHECK (type IN ('HOSPITAL', 'EMS_UNIT'))
);

CREATE INDEX idx_organizations_type_created
    ON organizations (type, created_at);

CREATE TABLE user_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    organization_id BIGINT NULL,
    login_id VARCHAR(30) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    last_login_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_public_id UNIQUE (public_id),
    CONSTRAINT uk_user_accounts_login_id UNIQUE (login_id),
    CONSTRAINT fk_user_accounts_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT chk_user_accounts_role
        CHECK (role IN ('SUPER_ADMIN', 'PARAMEDIC', 'HOSPITAL_STAFF')),
    CONSTRAINT chk_user_accounts_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_user_accounts_organization
        CHECK (
            (role = 'SUPER_ADMIN' AND organization_id IS NULL)
            OR
            (role IN ('PARAMEDIC', 'HOSPITAL_STAFF') AND organization_id IS NOT NULL)
        )
);

CREATE INDEX idx_user_accounts_organization_role
    ON user_accounts (organization_id, role);

CREATE TABLE invitation_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    role VARCHAR(30) NOT NULL,
    code_digest BINARY(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_by_account_id BIGINT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_by_account_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_invitation_codes_public_id UNIQUE (public_id),
    CONSTRAINT uk_invitation_codes_digest UNIQUE (code_digest),
    CONSTRAINT fk_invitation_codes_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_invitation_codes_used_by
        FOREIGN KEY (used_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_invitation_codes_created_by
        FOREIGN KEY (created_by_account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_invitation_codes_role
        CHECK (role IN ('PARAMEDIC', 'HOSPITAL_STAFF')),
    CONSTRAINT chk_invitation_codes_status
        CHECK (status IN ('AVAILABLE', 'USED', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_invitation_codes_status_expires
    ON invitation_codes (status, expires_at);

CREATE INDEX idx_invitation_codes_organization_created
    ON invitation_codes (organization_id, created_at);

CREATE TABLE hospital_profiles (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    organization_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 7) NOT NULL,
    longitude DECIMAL(10, 7) NOT NULL,
    contact VARCHAR(30) NOT NULL,
    receiving_status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_hospital_profiles_public_id UNIQUE (public_id),
    CONSTRAINT uk_hospital_profiles_organization UNIQUE (organization_id),
    CONSTRAINT uk_hospital_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_hospital_profiles_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_hospital_profiles_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT chk_hospital_profiles_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_hospital_profiles_longitude
        CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_hospital_profiles_receiving
        CHECK (receiving_status IN ('ON', 'OFF'))
);

CREATE INDEX idx_hospital_profiles_receiving
    ON hospital_profiles (receiving_status);

CREATE TABLE refresh_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    account_id BIGINT NOT NULL,
    token_digest BINARY(32) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    replacement_public_id CHAR(36) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_public_id UNIQUE (public_id),
    CONSTRAINT uk_refresh_tokens_digest UNIQUE (token_digest),
    CONSTRAINT fk_refresh_tokens_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id)
);

CREATE INDEX idx_refresh_tokens_account_expires
    ON refresh_tokens (account_id, expires_at);

CREATE TABLE audit_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id CHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    actor_account_id BIGINT NULL,
    actor_organization_id BIGINT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_public_id CHAR(36) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    trace_id VARCHAR(64) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_audit_events_public_id UNIQUE (public_id),
    CONSTRAINT fk_audit_events_actor_account
        FOREIGN KEY (actor_account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_audit_events_actor_organization
        FOREIGN KEY (actor_organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_audit_events_target_occurred
    ON audit_events (target_type, target_public_id, occurred_at);

CREATE INDEX idx_audit_events_action_occurred
    ON audit_events (action, occurred_at);
