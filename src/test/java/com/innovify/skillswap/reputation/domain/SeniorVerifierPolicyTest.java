package com.innovify.skillswap.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.reputation.domain.model.valueobjects.VerifierRank;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.reputation.domain.services.SeniorVerifierPolicy;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class SeniorVerifierPolicyTest {

    private static final DefaultVerifierReliabilityCalculator CALCULATOR = new DefaultVerifierReliabilityCalculator();

    @ParameterizedTest
    @CsvSource({"0, BRONZE", "29, BRONZE", "30, SILVER", "99, SILVER", "100, GOLD", "500, GOLD"})
    void rank_followsTheResolvedCases(int resolved, VerifierRank expected) {
        assertThat(VerifierRank.fromResolvedCases(resolved)).isEqualTo(expected);
    }

    @Test
    void rank_rejectsANegativeCount() {
        assertThatThrownBy(() -> VerifierRank.fromResolvedCases(-1)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @CsvSource({"100, 90, true", "100, 100, true", "100, 89, false", "99, 100, false", "250, 95, true"})
    void senior_needsTheGoldRankAndAReliabilityOf90(int resolved, int reliability, boolean expected) {
        assertThat(SeniorVerifierPolicy.isSenior(resolved, new ReliabilityScore(reliability))).isEqualTo(expected);
    }

    @Test
    void reliability_knowsItsRankAndWhetherItIsSenior() {
        VerifierReliability reliability = new VerifierReliability(4);
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(CALCULATOR);
        }
        assertThat(reliability.getRank()).isEqualTo(VerifierRank.GOLD);
        assertThat(reliability.isSeniorVerifier()).isTrue();

        reliability.recordOverturn(CALCULATOR);
        assertThat(reliability.getScore().value()).isEqualTo(85);
        assertThat(reliability.isSeniorVerifier()).isFalse();
    }
}
