package com.innovify.skillswap.credentialverification.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/**
 * Explainable, rule-based result of a certificate risk evaluation. The level is always derived from the
 * score: 0-19 low risk, 20-49 review, 50+ high risk.
 */
public record RiskAssessment(int score) {

    public static final int REVIEW_THRESHOLD = 20;
    public static final int HIGH_RISK_THRESHOLD = 50;

    public RiskAssessment {
        if (score < 0) {
            throw new DomainException("The risk score cannot be negative.");
        }
    }

    public RiskLevel level() {
        if (score >= HIGH_RISK_THRESHOLD) {
            return RiskLevel.HIGH_RISK;
        }
        return score >= REVIEW_THRESHOLD ? RiskLevel.REVIEW : RiskLevel.LOW_RISK;
    }
}
