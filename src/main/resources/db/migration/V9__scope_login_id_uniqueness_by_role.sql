ALTER TABLE user_accounts
    DROP INDEX uk_user_accounts_login_id;

ALTER TABLE user_accounts
    ADD CONSTRAINT uk_user_accounts_login_id_role UNIQUE (login_id, role);
