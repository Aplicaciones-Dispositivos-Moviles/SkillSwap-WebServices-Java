-- Assessment & Peer Review: the review of a case is due by the deadline of the plan the student had when it was
-- opened (48 hours on the monthly plan, 5 business days on the free plan), so it is stored with the case and a
-- later change of plan does not move it. The cases opened before the plans have no deadline (NULL).
ALTER TABLE verification_cases
    ADD COLUMN review_due_at timestamp with time zone;

-- The monthly escalation quota counts the cases a student opened since the start of the calendar month.
CREATE INDEX ix_verification_cases_student_id_opened_at ON verification_cases (student_id, opened_at);
