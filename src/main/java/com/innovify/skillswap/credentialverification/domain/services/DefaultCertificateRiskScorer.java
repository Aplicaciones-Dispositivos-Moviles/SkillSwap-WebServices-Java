package com.innovify.skillswap.credentialverification.domain.services;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;

/** Pure business rules with no external dependencies. */
public class DefaultCertificateRiskScorer implements CertificateRiskScorer {

    public static final int DUPLICATE_CERTIFICATE_NUMBER_POINTS = 30;
    public static final int DUPLICATE_VERIFICATION_CODE_POINTS = 30;
    public static final int OCR_INCONSISTENCIES_POINTS = 15;
    /** A file already registered by another student is enough, on its own, to be high risk. */
    public static final int DUPLICATE_FILE_HASH_POINTS = RiskAssessment.HIGH_RISK_THRESHOLD;
    /** A certificate issued to somebody else is enough, on its own, to be high risk. */
    public static final int HOLDER_NAME_MISMATCH_POINTS = RiskAssessment.HIGH_RISK_THRESHOLD;

    @Override
    public RiskAssessment calculateRisk(boolean duplicateCertificateNumber, boolean duplicateVerificationCode,
                                        boolean duplicateFileHash, boolean ocrInconsistencies,
                                        boolean holderNameMismatch) {
        int score = 0;
        if (duplicateCertificateNumber) {
            score += DUPLICATE_CERTIFICATE_NUMBER_POINTS;
        }
        if (duplicateVerificationCode) {
            score += DUPLICATE_VERIFICATION_CODE_POINTS;
        }
        if (ocrInconsistencies) {
            score += OCR_INCONSISTENCIES_POINTS;
        }
        if (duplicateFileHash) {
            score += DUPLICATE_FILE_HASH_POINTS;
        }
        if (holderNameMismatch) {
            score += HOLDER_NAME_MISMATCH_POINTS;
        }
        return new RiskAssessment(score);
    }
}
