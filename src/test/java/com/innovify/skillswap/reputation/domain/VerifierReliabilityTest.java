package com.innovify.skillswap.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VerifierReliabilityTest {

    private static final DefaultVerifierReliabilityCalculator CALCULATOR = new DefaultVerifierReliabilityCalculator();

    @Test
    void constructor_startsWithZeroCountersAndTheFullScore() {
        Instant before = Instant.now();

        VerifierReliability reliability = new VerifierReliability(3);

        assertThat(reliability.getVerifierUserId()).isEqualTo(3);
        assertThat(reliability.getResolvedCasesCount()).isZero();
        assertThat(reliability.getOverturnedDecisionsCount()).isZero();
        assertThat(reliability.getSanctionsCount()).isZero();
        assertThat(reliability.getScore().value()).isEqualTo(100);
        assertThat(reliability.getUpdatedAt()).isBetween(before, Instant.now());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_withAnInvalidUser_throwsDomainException(int userId) {
        assertThatThrownBy(() -> new VerifierReliability(userId)).isInstanceOf(DomainException.class);
    }

    @Test
    void recordResolution_countsTheCaseWithoutChangingTheScore() {
        VerifierReliability reliability = new VerifierReliability(3)
                .recordResolution(CALCULATOR).recordResolution(CALCULATOR);

        assertThat(reliability.getResolvedCasesCount()).isEqualTo(2);
        assertThat(reliability.getScore().value()).isEqualTo(100);
    }

    @Test
    void recordOverturn_countsItAndDiscountsFifteenPoints() {
        VerifierReliability reliability = new VerifierReliability(3).recordOverturn(CALCULATOR);

        assertThat(reliability.getOverturnedDecisionsCount()).isEqualTo(1);
        assertThat(reliability.getScore().value()).isEqualTo(85);
    }

    @Test
    void applySanction_countsItAndDiscountsTwentyFivePoints() {
        VerifierReliability reliability = new VerifierReliability(3).applySanction(CALCULATOR);

        assertThat(reliability.getSanctionsCount()).isEqualTo(1);
        assertThat(reliability.getScore().value()).isEqualTo(75);
    }

    @Test
    void events_combine() {
        VerifierReliability reliability = new VerifierReliability(3)
                .recordResolution(CALCULATOR)
                .recordResolution(CALCULATOR)
                .recordResolution(CALCULATOR)
                .recordOverturn(CALCULATOR)
                .applySanction(CALCULATOR);

        assertThat(reliability.getResolvedCasesCount()).isEqualTo(3);
        assertThat(reliability.getOverturnedDecisionsCount()).isEqualTo(1);
        assertThat(reliability.getSanctionsCount()).isEqualTo(1);
        assertThat(reliability.getScore().value()).isEqualTo(60);
    }

    @Test
    void score_neverDropsBelowZero() {
        VerifierReliability reliability = new VerifierReliability(3);

        for (int i = 0; i < 8; i++) {
            reliability.recordOverturn(CALCULATOR);
        }

        assertThat(reliability.getOverturnedDecisionsCount()).isEqualTo(8);
        assertThat(reliability.getScore().value()).isZero();
    }

    @Test
    void everyChange_refreshesTheUpdatedAt() {
        VerifierReliability reliability = new VerifierReliability(3);
        Instant before = Instant.now();

        reliability.recordResolution(CALCULATOR);

        assertThat(reliability.getUpdatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void withoutACalculator_throwsNullPointerException() {
        assertThatThrownBy(() -> new VerifierReliability(3).recordResolution(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerifierReliability(3).recordOverturn(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new VerifierReliability(3).applySanction(null))
                .isInstanceOf(NullPointerException.class);
    }

    @org.junit.jupiter.api.Test
    void recordMissedDeadline_countsItAndLowersTheScore() {
        var reliability = new com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability(3);

        reliability.recordMissedDeadline(CALCULATOR);

        org.assertj.core.api.Assertions.assertThat(reliability.getMissedDeadlinesCount()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(reliability.getScore().value()).isEqualTo(95);
    }
}
