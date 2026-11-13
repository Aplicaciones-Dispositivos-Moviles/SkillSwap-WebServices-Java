package com.innovify.skillswap.credentialverification.domain;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel;
import com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultCertificateRiskScorerTest {

    private final DefaultCertificateRiskScorer scorer = new DefaultCertificateRiskScorer();

    @ParameterizedTest(name = "number={0} code={1} hash={2} ocr={3} -> {4} {5}")
    @CsvSource({
            // number, code, hash, ocr, score, level
            "false,false,false,false,0,LOW_RISK",
            "false,false,true,false,50,HIGH_RISK",
            "false,false,false,true,15,LOW_RISK",
            "false,false,true,true,65,HIGH_RISK",
            "true,false,false,false,30,REVIEW",
            "false,true,false,false,30,REVIEW",
            "true,false,false,true,45,REVIEW",
            "true,true,false,false,60,HIGH_RISK",
            "true,false,true,true,95,HIGH_RISK",
            "true,true,true,true,125,HIGH_RISK"})
    void calculateRisk_addsThePointsOfEachRule(boolean number, boolean code, boolean hash, boolean ocr, int score,
                                               RiskLevel level) {
        RiskAssessment assessment = scorer.calculateRisk(number, code, hash, ocr);

        assertThat(assessment.score()).isEqualTo(score);
        assertThat(assessment.level()).isEqualTo(level);
    }

    @Test
    void duplicateFile_aloneIsHighRisk() {
        assertThat(scorer.calculateRisk(false, false, true, false).level()).isEqualTo(RiskLevel.HIGH_RISK);
    }

    @org.junit.jupiter.api.Test
    void holderNameMismatch_aloneIsHighRisk() {
        var scorer = new com.innovify.skillswap.credentialverification.domain.services.DefaultCertificateRiskScorer();

        var risk = scorer.calculateRisk(false, false, false, false, true);

        org.assertj.core.api.Assertions.assertThat(risk.level()).isEqualTo(
                com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel.HIGH_RISK);
        org.assertj.core.api.Assertions.assertThat(scorer.calculateRisk(false, false, false, false).score())
                .isZero();
    }
}
