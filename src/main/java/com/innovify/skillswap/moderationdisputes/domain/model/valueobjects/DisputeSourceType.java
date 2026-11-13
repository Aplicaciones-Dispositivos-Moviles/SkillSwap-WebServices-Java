package com.innovify.skillswap.moderationdisputes.domain.model.valueobjects;

import java.util.Optional;

/** Where an escalation to a Verificador senior comes from. Only CERTIFICATE_REVIEW is opened today. */
public enum DisputeSourceType {
    /** A certificate registered as suspicious by Credential Verification (opened by the system). */
    CERTIFICATE_REVIEW("CertificateReview"),
    /** A student appeals the decision of a verifier (reserved: appeals are still handled by the case itself). */
    VERIFIER_DECISION_APPEAL("VerifierDecisionAppeal"),
    /** A user reports the behavior of another (reserved). */
    USER_REPORT("UserReport");

    private final String value;

    DisputeSourceType(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static DisputeSourceType fromValue(String value) {
        return tryParse(value).orElseThrow(() -> new IllegalArgumentException("Unknown DisputeSourceType: " + value));
    }

    /** Case-insensitive lookup that answers empty for anything that is not a value of the enum. */
    public static Optional<DisputeSourceType> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (DisputeSourceType candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value.strip())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
