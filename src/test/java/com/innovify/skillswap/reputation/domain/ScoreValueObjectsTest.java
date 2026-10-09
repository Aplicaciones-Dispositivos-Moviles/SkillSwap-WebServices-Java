package com.innovify.skillswap.reputation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.reputation.domain.model.valueobjects.EmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScoreValueObjectsTest {

    @ParameterizedTest
    @ValueSource(ints = {0, 55, 100})
    void reliabilityScore_withAValueInRange_keepsIt(int value) {
        assertThat(new ReliabilityScore(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void reliabilityScore_outOfRange_throwsDomainException(int value) {
        assertThatThrownBy(() -> new ReliabilityScore(value)).isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 40, 100})
    void employabilityScore_withAValueInRange_keepsIt(int value) {
        assertThat(new EmployabilityScore(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void employabilityScore_outOfRange_throwsDomainException(int value) {
        assertThatThrownBy(() -> new EmployabilityScore(value)).isInstanceOf(DomainException.class);
    }
}
