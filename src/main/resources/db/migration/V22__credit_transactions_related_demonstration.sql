-- A verifier earns credits for reviewing a final demonstration (US29). It has its own column because the
-- ids of cases and demonstrations come from different tables and could collide.
ALTER TABLE credit_transactions ADD COLUMN related_demonstration_id integer;

-- A demonstration pays a wallet only once.
CREATE UNIQUE INDEX ux_credit_transactions_wallet_demonstration
    ON credit_transactions (wallet_id, related_demonstration_id)
    WHERE related_demonstration_id IS NOT NULL;

-- A movement comes from a case or from a demonstration, never from both.
ALTER TABLE credit_transactions ADD CONSTRAINT ck_credit_transactions_single_origin
    CHECK (related_case_id IS NULL OR related_demonstration_id IS NULL);
