package com.innovify.skillswap.recognitionincentives.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.RedemptionIdempotencyKey;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.IdempotencyKey;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class IdempotencyKeyTest {

    @Test
    void tryParse_keepsTheKeyExactlyAsSent() {
        assertThat(IdempotencyKey.tryParse("7f3c-A1")).contains(new IdempotencyKey("7f3c-A1"));
    }

    @Test
    void tryParse_acceptsSixtyFourCharacters() {
        assertThat(IdempotencyKey.tryParse("k".repeat(64))).isPresent();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    void tryParse_aBlankKey_isEmpty(String value) {
        assertThat(IdempotencyKey.tryParse(value)).isEmpty();
    }

    @Test
    void tryParse_moreThanSixtyFourCharacters_isEmpty() {
        assertThat(IdempotencyKey.tryParse("k".repeat(65))).isEmpty();
    }

    @Test
    void constructor_withAnInvalidKey_throwsDomainException() {
        assertThatThrownBy(() -> new IdempotencyKey(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(65))).isInstanceOf(DomainException.class);
    }

    @Test
    void redemptionIdempotencyKey_pointsToTheMovementOfTheUser() {
        var stored = new RedemptionIdempotencyKey(3, new IdempotencyKey("abc"), 9);

        assertThat(stored.getUserId()).isEqualTo(3);
        assertThat(stored.getKey()).isEqualTo(new IdempotencyKey("abc"));
        assertThat(stored.getCreditTransactionId()).isEqualTo(9);
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    @Test
    void redemptionIdempotencyKey_withoutUserKeyOrMovement_throwsDomainException() {
        assertThatThrownBy(() -> new RedemptionIdempotencyKey(0, new IdempotencyKey("abc"), 9))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RedemptionIdempotencyKey(3, null, 9)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new RedemptionIdempotencyKey(3, new IdempotencyKey("abc"), 0))
                .isInstanceOf(DomainException.class);
    }
}
