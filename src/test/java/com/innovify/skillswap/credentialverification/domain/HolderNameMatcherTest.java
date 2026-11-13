package com.innovify.skillswap.credentialverification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.domain.services.HolderNameMatcher;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolderNameMatcherTest {

    private final HolderNameMatcher matcher = new HolderNameMatcher();

    @ParameterizedTest(name = "\"{0}\" is \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Ana María Pérez García | Ana María Pérez García",
            "ANA MARIA PEREZ GARCIA | Ana María Pérez García",
            "PÉREZ GARCÍA, Ana María | Ana María Pérez García",
            "Ana Pérez | Ana María Pérez García",
            "Ana M. Pérez García | Ana María Pérez García",
            "Ana Pérez-García | Ana Pérez García",
            "María de los Ángeles Núñez | Maria Angeles Nunez",
            "Ana | Ana",
            "José Ñique | JOSE NIQUE"
    })
    void matches_theSamePersonWrittenDifferently(String holder, String registered) {
        assertThat(matcher.matches(holder, registered)).isTrue();
        assertThat(matcher.matches(registered, holder)).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is not \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "Luis Gómez | Ana María Pérez García",
            "Ana | Ana María Pérez García",
            "Ana Gómez | Ana María Pérez García",
            "A. P. | Ana Pérez",
            "Ana Pérez Torres | Ana Pérez García"
    })
    void doesNotMatch_anotherPersonOrANameThatIdentifiesNobody(String holder, String registered) {
        assertThat(matcher.matches(holder, registered)).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {"null | Ana Pérez", "Ana Pérez | null",
            "'  ' | Ana Pérez", "... | Ana Pérez"})
    void aMissingName_isNotReportedAsDifferent(String holder, String registered) {
        assertThat(matcher.matches(holder, registered)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', nullValues = "null", value = {"null | false", "'  ' | false", "-- | false",
            "Ana | true"})
    void isComparable_onlyWhenSomethingIsLeftAfterNormalizing(String name, boolean expected) {
        assertThat(matcher.isComparable(name)).isEqualTo(expected);
    }
}
