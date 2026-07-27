CREATE TABLE organizations (
    id VARCHAR(36) NOT NULL,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_organizations_name UNIQUE (name)
);

CREATE TABLE user_accounts (
    id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NULL,
    role VARCHAR(32) NOT NULL,
    login_id VARCHAR(80) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_login_at TIMESTAMP(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_accounts_login_id UNIQUE (login_id),
    CONSTRAINT fk_user_accounts_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id)
);

CREATE INDEX idx_user_accounts_organization_id
    ON user_accounts (organization_id);

CREATE TABLE invitation_codes (
    id VARCHAR(36) NOT NULL,
    organization_id VARCHAR(36) NOT NULL,
    target_role VARCHAR(32) NOT NULL,
    code_hash VARCHAR(100) NOT NULL,
    status VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    issued_by VARCHAR(36) NOT NULL,
    issued_at TIMESTAMP(6) NOT NULL,
    used_by VARCHAR(36) NULL,
    used_at TIMESTAMP(6) NULL,
    revoked_by VARCHAR(36) NULL,
    revoked_at TIMESTAMP(6) NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_invitation_codes_organization
        FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_invitation_codes_issued_by
        FOREIGN KEY (issued_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_invitation_codes_used_by
        FOREIGN KEY (used_by) REFERENCES user_accounts (id),
    CONSTRAINT fk_invitation_codes_revoked_by
        FOREIGN KEY (revoked_by) REFERENCES user_accounts (id)
);

CREATE INDEX idx_invitation_codes_status_expires_at
    ON invitation_codes (status, expires_at);

CREATE INDEX idx_invitation_codes_organization_id
    ON invitation_codes (organization_id);

CREATE TABLE refresh_tokens (
    id VARCHAR(36) NOT NULL,
    account_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    replaced_by_token_id VARCHAR(36) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_account
        FOREIGN KEY (account_id) REFERENCES user_accounts (id),
    CONSTRAINT fk_refresh_tokens_replaced_by
        FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
);

CREATE INDEX idx_refresh_tokens_account_id
    ON refresh_tokens (account_id);
