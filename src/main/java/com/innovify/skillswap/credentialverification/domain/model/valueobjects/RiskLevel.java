package com.innovify.skillswap.credentialverification.domain.model.valueobjects;

/** Risk level derived from a {@link RiskAssessment} score. */
public enum RiskLevel {
    LOW_RISK("LowRisk"),
    REVIEW("Review"),
    HIGH_RISK("HighRisk");

    private final String value;

    RiskLevel(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static RiskLevel fromValue(String value) {
        for (RiskLevel candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown RiskLevel: " + value);
    }
}
