package com.innovify.skillswap.recognitionincentives.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WalletTest {

    @Test
    void constructor_startsEmpty() {
        Wallet wallet = new Wallet(3);

        assertThat(wallet.getWalletOwnerId()).isEqualTo(3);
        assertThat(wallet.getBalance()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void constructor_withAnInvalidOwner_throwsDomainException(int ownerId) {
        assertThatThrownBy(() -> new Wallet(ownerId)).isInstanceOf(DomainException.class);
    }

    @Test
    void credit_addsToTheBalance() {
        Wallet wallet = new Wallet(3).credit(new Credits(10)).credit(new Credits(5));

        assertThat(wallet.getBalance()).isEqualTo(15);
    }

    @Test
    void credit_withZero_throwsDomainException() {
        assertThatThrownBy(() -> new Wallet(3).credit(new Credits(0))).isInstanceOf(DomainException.class);
    }

    @Test
    void credit_pastTheMaximum_throwsDomainExceptionAndKeepsTheBalance() {
        Wallet wallet = new Wallet(3).credit(new Credits(Integer.MAX_VALUE));

        assertThatThrownBy(() -> wallet.credit(new Credits(1))).isInstanceOf(DomainException.class);
        assertThat(wallet.getBalance()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void debit_takesFromTheBalance() {
        Wallet wallet = new Wallet(3).credit(new Credits(30)).debit(new Credits(30));

        assertThat(wallet.getBalance()).isZero();
    }

    @Test
    void debit_withZero_throwsDomainException() {
        Wallet wallet = new Wallet(3).credit(new Credits(30));

        assertThatThrownBy(() -> wallet.debit(new Credits(0))).isInstanceOf(DomainException.class);
    }

    @Test
    void debit_moreThanTheBalance_throwsDomainExceptionAndKeepsTheBalance() {
        Wallet wallet = new Wallet(3).credit(new Credits(20));

        assertThatThrownBy(() -> wallet.debit(new Credits(30))).isInstanceOf(DomainException.class);
        assertThat(wallet.getBalance()).isEqualTo(20);
    }

    @Test
    void canAfford_comparesTheBalanceWithTheAmount() {
        Wallet wallet = new Wallet(3).credit(new Credits(30));

        assertThat(wallet.canAfford(new Credits(30))).isTrue();
        assertThat(wallet.canAfford(new Credits(31))).isFalse();
    }
}
