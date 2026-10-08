package com.innovify.skillswap.credentialverification.domain.services;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;

/** Domain service: calculates the explainable risk of a certificate from a set of rules. */
public interface CertificateRiskScorer {

    RiskAssessment calculateRisk(boolean duplicateCertificateNumber, boolean duplicateVerificationCode,
                                 boolean duplicateFileHash, boolean ocrInconsistencies);
}
