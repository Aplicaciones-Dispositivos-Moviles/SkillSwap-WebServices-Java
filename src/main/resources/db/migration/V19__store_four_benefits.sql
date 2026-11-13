-- Recognition & Incentives: the store now has four benefits (Badge, ContributionCertificate, AdvancedPathUnlock and
-- SponsoredCertification). The old CHECK only allowed two of them.
ALTER TABLE credit_transactions DROP CONSTRAINT ck_credit_transactions_redemption_item;

ALTER TABLE credit_transactions
    ADD CONSTRAINT ck_credit_transactions_redemption_item CHECK (redemption_item IS NULL
        OR (type = 'Redeemed' AND redemption_item IN
            ('Badge', 'ContributionCertificate', 'AdvancedPathUnlock', 'SponsoredCertification')));

-- The badge can be redeemed only once per wallet (the command service checks it under the wallet lock; this index
-- is the last line of defence).
CREATE UNIQUE INDEX ux_credit_transactions_badge_per_wallet ON credit_transactions (wallet_id)
    WHERE redemption_item = 'Badge';
