package com.innovify.skillswap.credentialverification.domain.services;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;

/** Domain service: calculates the explainable risk of a certificate from a set of rules. */
public interface CertificateRiskScorer {

    /**
     * @param holderNameMismatch whether the holder read from the certificate is not the registered name of the
     *                           student who uploads it
     */
    RiskAssessment calculateRisk(boolean duplicateCertificateNumber, boolean duplicateVerificationCode,
                                 boolean duplicateFileHash, boolean ocrInconsistencies, boolean holderNameMismatch);

    /** Same as above for a certificate whose holder matches its owner, or could not be compared. */
    default RiskAssessment calculateRisk(boolean duplicateCertificateNumber, boolean duplicateVerificationCode,
                                         boolean duplicateFileHash, boolean ocrInconsistencies) {
        return calculateRisk(duplicateCertificateNumber, duplicateVerificationCode, duplicateFileHash,
                ocrInconsistencies, false);
    }
}
