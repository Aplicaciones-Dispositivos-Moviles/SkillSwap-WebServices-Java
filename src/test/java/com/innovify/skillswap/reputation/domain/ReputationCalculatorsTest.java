package com.innovify.skillswap.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.reputation.domain.services.DefaultEmployabilityScoreCalculator;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReputationCalculatorsTest {

    private final DefaultEmployabilityScoreCalculator employability = new DefaultEmployabilityScoreCalculator();
    private final DefaultVerifierReliabilityCalculator reliability = new DefaultVerifierReliabilityCalculator();

    // ---------- Reliability ----------

    @ParameterizedTest
    @CsvSource({"0,0,0,100", "40,0,0,100", "5,1,0,85", "5,2,0,70", "5,0,1,75", "5,1,1,60", "5,2,2,20"})
    void reliability_startsFromFullAndDiscountsOverturnsAndSanctions(int resolved, int overturned, int sanctions,
                                                                     int expected) {
        assertThat(reliability.calculate(resolved, overturned, sanctions).value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"0,7,0", "0,0,4", "0,100,100", "0,2147483647,2147483647"})
    void reliability_neverDropsBelowZero(int resolved, int overturned, int sanctions) {
        assertThat(reliability.calculate(resolved, overturned, sanctions).value()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"-1,0,0", "0,-1,0", "0,0,-1"})
    void reliability_withANegativeCounter_throwsDomainException(int resolved, int overturned, int sanctions) {
        assertThatThrownBy(() -> reliability.calculate(resolved, overturned, sanctions))
                .isInstanceOf(DomainException.class);
    }

    // ---------- Employability ----------

    @ParameterizedTest
    @CsvSource({"0,0", "1,10", "5,50", "10,100", "15,100", "2147483647,100"})
    void employability_givesTenPointsPerSkillUpToOneHundred(int skills, int expected) {
        assertThat(employability.calculate(skills).value()).isEqualTo(expected);
    }

    @Test
    void employability_withANegativeCount_throwsDomainException() {
        assertThatThrownBy(() -> employability.calculate(-1)).isInstanceOf(DomainException.class);
    }

    @org.junit.jupiter.api.Test
    void reliability_discountsFivePointsPerMissedDeadline() {
        org.assertj.core.api.Assertions.assertThat(reliability.calculate(10, 0, 0, 1).value()).isEqualTo(95);
        org.assertj.core.api.Assertions.assertThat(reliability.calculate(10, 1, 0, 2).value()).isEqualTo(75);
        org.assertj.core.api.Assertions.assertThat(reliability.calculate(10, 0, 0, 30).value()).isZero();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reliability.calculate(0, 0, 0, -1))
                .isInstanceOf(com.innovify.skillswap.shared.domain.exceptions.DomainException.class);
    }
}
