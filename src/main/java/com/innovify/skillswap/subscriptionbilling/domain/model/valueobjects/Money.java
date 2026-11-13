package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * An amount of real money in a currency, so amounts of different currencies are never mixed. It is never
 * negative and keeps two decimals.
 *
 * @param amount   the amount, rounded to cents
 * @param currency the ISO 4217 code, in upper case (PEN)
 */
public record Money(BigDecimal amount, String currency) {

    public static final String PEN = "PEN";

    public Money {
        if (amount == null) {
            throw new DomainException("The amount is required.");
        }
        if (amount.signum() < 0) {
            throw new DomainException("The amount cannot be negative.");
        }
        String code = currency == null ? "" : currency.strip().toUpperCase(Locale.ROOT);
        if (!code.matches("[A-Z]{3}")) {
            throw new DomainException("The currency must be a three-letter ISO 4217 code.");
        }

        amount = amount.setScale(2, RoundingMode.HALF_UP);
        currency = code;
    }

    /** An amount in Peruvian soles. */
    public static Money soles(String amount) {
        return new Money(new BigDecimal(amount), PEN);
    }
}
