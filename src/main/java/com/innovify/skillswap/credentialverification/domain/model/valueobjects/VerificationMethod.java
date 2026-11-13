package com.innovify.skillswap.credentialverification.domain.model.valueobjects;

/**
 * Mechanisms contemplated to verify a certificate. Only {@link #OCR_ONLY} and {@link #MANUAL} are executed in
 * the implemented scope; the others are documented as future extensions, with no active integration logic.
 */
public enum VerificationMethod {
    OCR_ONLY("OcrOnly"),
    QR("Qr"),
    ISSUER_URL("IssuerUrl"),
    OFFICIAL_REGISTRY("OfficialRegistry"),
    MANUAL("Manual");

    private final String value;

    VerificationMethod(String value) {
        this.value = value;
    }

    /** The representation stored in the database and exposed by the API. */
    public String value() {
        return value;
    }

    /** Case-insensitive lookup by {@link #value()}. */
    public static VerificationMethod fromValue(String value) {
        for (VerificationMethod candidate : values()) {
            if (candidate.value.equalsIgnoreCase(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown VerificationMethod: " + value);
    }
}
