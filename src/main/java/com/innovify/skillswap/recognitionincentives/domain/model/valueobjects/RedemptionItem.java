package com.innovify.skillswap.recognitionincentives.domain.model.valueobjects;

import java.util.Optional;

/** The benefits that can be redeemed with SkillCredits. */
public enum RedemptionItem {
    /**
     * Unlocks an advanced learning path for the student: it is completed like any other path, but it does not count
     * toward the limits of the plan (delivered by Learning Path Engine).
     */
    ADVANCED_PATH_UNLOCK("AdvancedPathUnlock", "advanced path unlock"),
    /** An exportable certificate of the contribution as a verifier. */
    CONTRIBUTION_CERTIFICATE("ContributionCertificate", "contribution certificate");

    private final String value;
    private final String description;

    RedemptionItem(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /** The name exposed by the API. */
    public String value() {
        return value;
    }

    /** What the benefit is, for the description of the movement. */
    public String description() {
        return description;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static RedemptionItem fromValue(String value) {
        return tryParse(value).orElseThrow(() -> new IllegalArgumentException("Unknown RedemptionItem: " + value));
    }

    /** Case-insensitive lookup that answers empty for anything that is not a benefit. */
    public static Optional<RedemptionItem> tryParse(String value) {
        if (value == null) {
            return Optional.empty();
        }
        for (RedemptionItem candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value.strip())) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
