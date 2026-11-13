package com.innovify.skillswap.iam.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsernameTest {

    @Test
    void constructor_normalizesToLowercaseAndTrims() {
        assertThat(new Username("  Ana_Perez ").value()).isEqualTo("ana_perez");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "ab", "with space"})
    void constructor_withInvalidUsername_throwsDomainException(String value) {
        assertThatThrownBy(() -> new Username(value)).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withMoreThan100Characters_throwsDomainException() {
        assertThatThrownBy(() -> new Username("a".repeat(101))).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withExactly100Characters_isAccepted() {
        assertThat(new Username("a".repeat(100)).value()).hasSize(100);
    }

    @Test
    void usernamesDifferingOnlyInCaseAreEqual() {
        assertThat(new Username("Ana")).isEqualTo(new Username("ana"));
    }

    @Test
    void isValid_withNull_returnsFalse() {
        assertThat(Username.isValid(null)).isFalse();
    }
}
