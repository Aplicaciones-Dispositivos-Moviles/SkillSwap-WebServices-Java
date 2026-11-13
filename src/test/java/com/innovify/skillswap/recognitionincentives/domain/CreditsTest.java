package com.innovify.skillswap.recognitionincentives.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

class CreditsTest {

    @Test
    void constructor_withAPositiveAmount_isPositive() {
        assertThat(new Credits(10).value()).isEqualTo(10);
        assertThat(new Credits(10).isPositive()).isTrue();
    }

    @Test
    void constructor_withZero_isNotPositive() {
        assertThat(new Credits(0).isPositive()).isFalse();
    }

    @Test
    void constructor_withANegativeAmount_throwsDomainException() {
        assertThatThrownBy(() -> new Credits(-1)).isInstanceOf(DomainException.class);
    }
}
