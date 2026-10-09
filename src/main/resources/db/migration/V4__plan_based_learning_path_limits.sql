-- Learning Path Engine: how many paths a student keeps is decided by their plan (free: 1 active and 3 in total;
-- monthly: 3 active and no total cap), so the rule "one active path per student" leaves the database and is
-- enforced by the application, which serializes the requests of each student with a transaction-level advisory
-- lock (pg_advisory_xact_lock). A path can now be 'Paused'.

DROP INDEX IF EXISTS ux_learning_paths_one_active_per_student;

-- When the student last advanced on the path: after a downgrade, the one with the most recent progress stays
-- active. The existing paths start from their last change.
ALTER TABLE learning_paths
    ADD COLUMN last_progress_at timestamp with time zone;

UPDATE learning_paths SET last_progress_at = updated_at;

ALTER TABLE learning_paths
    ALTER COLUMN last_progress_at SET NOT NULL;

ALTER TABLE learning_paths
    ADD CONSTRAINT ck_learning_paths_status CHECK (status IN ('Active', 'Paused', 'Completed'));

-- Counting the paths of a student, by status.
CREATE INDEX ix_learning_paths_student_id_status ON learning_paths (student_id, status);
