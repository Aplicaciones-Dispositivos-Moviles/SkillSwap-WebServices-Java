-- Assessment & Peer Review: a verification case is opened by a failed quiz attempt (attempt_id) or by the delivery of
-- a mini-project (submission_id, a logical reference to mini_project_submissions: the contexts of the same table
-- family do not use foreign keys between aggregates). Exactly one of the two is set.
ALTER TABLE verification_cases
    ADD COLUMN submission_id integer;

ALTER TABLE verification_cases
    ALTER COLUMN attempt_id DROP NOT NULL;

-- One case per attempt and one case per delivery. The partial indexes ignore the rows of the other origin.
DROP INDEX IF EXISTS ux_verification_cases_attempt;
CREATE UNIQUE INDEX ux_verification_cases_attempt
    ON verification_cases (attempt_id) WHERE attempt_id IS NOT NULL;
CREATE UNIQUE INDEX ux_verification_cases_submission
    ON verification_cases (submission_id) WHERE submission_id IS NOT NULL;

-- Every case that exists today was opened by an attempt, so the constraint holds for the current rows.
ALTER TABLE verification_cases
    ADD CONSTRAINT ck_verification_cases_origin CHECK ((attempt_id IS NULL) <> (submission_id IS NULL));
