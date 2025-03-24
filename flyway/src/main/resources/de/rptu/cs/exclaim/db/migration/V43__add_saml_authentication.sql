-- unique identifier issued by SAML identity provider
ALTER TABLE users ADD COLUMN samlid VARCHAR(255) NULL CONSTRAINT u__users__samlid UNIQUE;

-- accounts created via SAML login have no username or password
ALTER TABLE users ALTER COLUMN username DROP NOT NULL;
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;

-- ensure that all accounts have a valid login method
ALTER TABLE users ADD CONSTRAINT check__users__require_valid_login_method CHECK (
    -- login via username and password (also allows samlid to be set)
    (username IS NOT NULL AND password IS NOT NULL) OR
    -- login only via SAML
    (samlid IS NOT NULL AND password IS NULL)
    -- violations:
    --  neither samlid nor password
    --  password without username
);
