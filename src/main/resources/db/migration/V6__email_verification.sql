-- Identity & Access: email verification of the institutional email (US01, US02).
--
-- The token of the verification link is never stored: only its SHA-256 (hexadecimal), with its expiration. A new
-- email replaces the previous token, and verifying the account clears it, so each link works once.
-- verification_email_sent_at limits how often a verification email can be sent to the same account.
ALTER TABLE users
    ADD COLUMN verification_token_hash character varying(64);

ALTER TABLE users
    ADD COLUMN verification_token_expires_at timestamp with time zone;

ALTER TABLE users
    ADD COLUMN verification_email_sent_at timestamp with time zone;

CREATE UNIQUE INDEX ux_users_verification_token_hash ON users (verification_token_hash)
    WHERE verification_token_hash IS NOT NULL;

-- From now on an unverified account cannot sign in. Every account created before this migration (the demo and
-- test accounts of the team) never received a verification email, so all of them are marked as verified and keep
-- working.
UPDATE users SET is_verified = true WHERE is_verified = false;
