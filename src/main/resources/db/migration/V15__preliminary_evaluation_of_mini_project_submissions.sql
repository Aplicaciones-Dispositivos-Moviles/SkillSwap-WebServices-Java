-- Assessment & Peer Review: the preliminary evaluation of a mini-project delivery by the AI (US19). A delivery is received
-- as 'Pending' and the evaluation settles it once: 'Approved' (the node is completed without a verifier), 'Escalated'
-- (a verification case was opened) or 'EscalationLimitReached' (it needed a verifier, but the plan had no escalations
-- left). The score and the confidence (0 to 100) and the feedback of the AI are null when it could not evaluate it.
ALTER TABLE mini_project_submissions
    ADD COLUMN evaluation_status character varying(30) NOT NULL DEFAULT 'Pending',
    ADD COLUMN preliminary_score integer,
    ADD COLUMN preliminary_confidence integer,
    ADD COLUMN preliminary_feedback character varying(1000),
    ADD COLUMN evaluated_at timestamp with time zone;

-- Until this version every delivery opened its case right away, unless the plan had no escalations left (V14). Those
-- deliveries were never evaluated by the AI, so they keep empty scores.
UPDATE mini_project_submissions s
SET evaluation_status = CASE
        WHEN EXISTS (SELECT 1 FROM verification_cases c WHERE c.submission_id = s.id) THEN 'Escalated'
        ELSE 'EscalationLimitReached'
    END,
    evaluated_at = s.submitted_at;

-- From now on the application always sets the status.
ALTER TABLE mini_project_submissions
    ALTER COLUMN evaluation_status DROP DEFAULT;

ALTER TABLE mini_project_submissions
    ADD CONSTRAINT ck_mini_project_submissions_evaluation_status
        CHECK (evaluation_status IN ('Pending', 'Approved', 'Escalated', 'EscalationLimitReached'));

ALTER TABLE mini_project_submissions
    ADD CONSTRAINT ck_mini_project_submissions_preliminary_score
        CHECK (preliminary_score IS NULL OR preliminary_score BETWEEN 0 AND 100);

ALTER TABLE mini_project_submissions
    ADD CONSTRAINT ck_mini_project_submissions_preliminary_confidence
        CHECK (preliminary_confidence IS NULL OR preliminary_confidence BETWEEN 0 AND 100);
