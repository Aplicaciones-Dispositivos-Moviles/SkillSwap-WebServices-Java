-- Assessment & Peer Review: a Verificador senior can suspend a verifier whose reliability is low (T61). A suspended
-- verifier receives no more cases until a senior reinstates them. It is a flag apart from 'available' (chosen by the
-- verifier) and 'verified' (moderation), so none of them can undo it.
ALTER TABLE verifier_profiles
    ADD COLUMN suspended boolean NOT NULL DEFAULT false,
    ADD COLUMN suspended_at timestamp with time zone,
    ADD COLUMN suspension_reason character varying(2000),
    ADD COLUMN suspended_by integer;
