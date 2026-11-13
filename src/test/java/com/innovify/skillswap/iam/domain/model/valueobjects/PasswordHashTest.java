package com.innovify.skillswap.iam.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordHashTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void constructor_withBlankHash_throwsDomainException(String value) {
        assertThatThrownBy(() -> new PasswordHash(value)).isInstanceOf(DomainException.class);
    }

    @Test
    void toString_neverPrintsTheHash() {
        assertThat(new PasswordHash("$2a$11$secret").toString()).doesNotContain("secret");
    }
}
