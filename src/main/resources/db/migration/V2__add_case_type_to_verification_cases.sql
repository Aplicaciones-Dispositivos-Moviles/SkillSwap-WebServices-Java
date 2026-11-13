-- The type of the work under review in a verification case: "Quiz" or "MiniProject", stored as text like the
-- other states of the case (status, decision). It sets the SkillCredits the verifier earns when resolving it.
--
-- Backfill: every existing case was opened by a failed quiz attempt (attempt_id is NOT NULL and points to
-- assessment_attempts), because the platform has no mini-project deliverable yet, so all of them are 'Quiz'.
-- The default only fills those rows and is dropped right after: from now on the application always sets the type.
ALTER TABLE verification_cases
    ADD COLUMN case_type character varying(20) NOT NULL DEFAULT 'Quiz';

ALTER TABLE verification_cases
    ALTER COLUMN case_type DROP DEFAULT;

ALTER TABLE verification_cases
    ADD CONSTRAINT ck_verification_cases_case_type CHECK (case_type IN ('Quiz', 'MiniProject'));
