package com.innovify.skillswap.credentialverification.domain;

import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskAssessment;
import com.innovify.skillswap.credentialverification.domain.model.valueobjects.RiskLevel;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RiskAssessmentTest {

    @ParameterizedTest
    @CsvSource({"0,LOW_RISK", "19,LOW_RISK", "20,REVIEW", "49,REVIEW", "50,HIGH_RISK", "85,HIGH_RISK"})
    void level_isDerivedFromTheScore(int score, RiskLevel expected) {
        RiskAssessment assessment = new RiskAssessment(score);

        assertThat(assessment.score()).isEqualTo(score);
        assertThat(assessment.level()).isEqualTo(expected);
    }

    @Test
    void constructor_withNegativeScore_throwsDomainException() {
        assertThatThrownBy(() -> new RiskAssessment(-1)).isInstanceOf(DomainException.class);
    }
}
