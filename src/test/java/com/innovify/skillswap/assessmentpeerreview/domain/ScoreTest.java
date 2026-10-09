package com.innovify.skillswap.assessmentpeerreview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.Score;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScoreTest {

    @ParameterizedTest
    @CsvSource({"0,5", "3,5", "5,5"})
    void constructor_withAValidScore_keepsIt(int value, int total) {
        Score score = new Score(value, total);

        assertThat(score.value()).isEqualTo(value);
        assertThat(score.total()).isEqualTo(total);
    }

    @ParameterizedTest
    @CsvSource({"-1,5", "6,5", "0,0", "0,-1"})
    void constructor_withAnInvalidScore_throwsDomainException(int value, int total) {
        assertThatThrownBy(() -> new Score(value, total)).isInstanceOf(DomainException.class);
    }
}
