/*
The "code" column previously had two purposes:
- Storing the code for the account activation process (if the "verified" column is false)
- Storing a password reset token (if the "verified" column is true)

To limit the validity of password reset tokens, we now store those in a separate table.
*/

CREATE TABLE password_resets
(
    userid       INT           NOT NULL,
    code         VARCHAR(100)  NOT NULL,
    valid_until  TIMESTAMP     NOT NULL,

    CONSTRAINT pk__password_resets PRIMARY KEY (userid),
    CONSTRAINT fk__password_resets__users FOREIGN KEY (userid) REFERENCES users(userid)
);

-- Activated accounts with code -> move code to password_resets
INSERT INTO password_resets (userid, code, valid_until)
SELECT                       userid, code, CURRENT_TIMESTAMP + INTERVAL '1' DAY
FROM users
WHERE verified AND code IS NOT NULL;

UPDATE users SET code = NULL WHERE verified;

-- All users without an activation code are verified
ALTER TABLE users RENAME COLUMN code TO activation_code;
ALTER TABLE users DROP COLUMN verified;
