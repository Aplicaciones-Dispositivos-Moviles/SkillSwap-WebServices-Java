package com.innovify.skillswap.recognitionincentives.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.entities.CreditTransaction;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.TransactionType;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CreditTransactionTest {

    @Test
    void constructor_recordsAnEarnedMovementWithItsCase() {
        Instant before = Instant.now();

        CreditTransaction transaction = new CreditTransaction(2, new Credits(10), TransactionType.EARNED,
                "  Verification case resolved  ", 7);

        assertThat(transaction.getWalletId()).isEqualTo(2);
        assertThat(transaction.getAmount().value()).isEqualTo(10);
        assertThat(transaction.getType()).isEqualTo(TransactionType.EARNED);
        assertThat(transaction.getDescription()).isEqualTo("Verification case resolved");
        assertThat(transaction.getRelatedCaseId()).isEqualTo(7);
        assertThat(transaction.getCreatedAt()).isBetween(before, Instant.now());
    }

    @Test
    void constructor_recordsARedeemedMovementWithoutCase() {
        CreditTransaction transaction = new CreditTransaction(2, new Credits(30), TransactionType.REDEEMED,
                "Redeemed: contribution certificate", null);

        assertThat(transaction.getRelatedCaseId()).isNull();
    }

    @Test
    void constructor_withAnInvalidWallet_throwsDomainException() {
        assertThatThrownBy(() -> new CreditTransaction(0, new Credits(10), TransactionType.EARNED, "x", null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withAZeroOrMissingAmount_throwsDomainException() {
        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(0), TransactionType.EARNED, "x", null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CreditTransaction(2, null, TransactionType.EARNED, "x", null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withoutType_throwsDomainException() {
        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(10), null, "x", null))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void constructor_withAnEmptyDescription_throwsDomainException(String description) {
        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(10), TransactionType.EARNED, description,
                null)).isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withAMissingDescription_throwsDomainException() {
        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(10), TransactionType.EARNED, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_withATooLongDescription_throwsDomainException() {
        String tooLong = "a".repeat(CreditTransaction.MAX_DESCRIPTION_LENGTH + 1);

        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(10), TransactionType.EARNED, tooLong, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void constructor_acceptsADescriptionOfTheMaximumLength() {
        String longest = "a".repeat(CreditTransaction.MAX_DESCRIPTION_LENGTH);

        assertThat(new CreditTransaction(2, new Credits(10), TransactionType.EARNED, longest, null)
                .getDescription()).hasSize(CreditTransaction.MAX_DESCRIPTION_LENGTH);
    }

    @Test
    void constructor_withACaseOnARedeemedMovement_throwsDomainException() {
        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(30), TransactionType.REDEEMED, "x", 7))
                .isInstanceOf(DomainException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -3})
    void constructor_withAnInvalidCase_throwsDomainException(int caseId) {
        assertThatThrownBy(() -> new CreditTransaction(2, new Credits(10), TransactionType.EARNED, "x", caseId))
                .isInstanceOf(DomainException.class);
    }
}
