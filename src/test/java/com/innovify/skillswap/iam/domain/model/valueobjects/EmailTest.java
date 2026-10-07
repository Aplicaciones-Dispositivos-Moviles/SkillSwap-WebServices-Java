package com.innovify.skillswap.iam.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @ParameterizedTest
    @ValueSource(strings = {"ana@upc.edu.pe", "ana.perez@pucp.edu.pe", "u202012345@upc.edu.pe"})
    void constructor_withInstitutionalEmail_createsEmail(String value) {
        assertThat(new Email(value).value()).isEqualTo(value);
    }

    @Test
    void constructor_normalizesToLowercaseAndTrims() {
        assertThat(new Email("  Ana@UPC.EDU.PE ").value()).isEqualTo("ana@upc.edu.pe");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "ana@gmail.com",
            "ana@upc.edu",
            "ana@edu.pe",
            "ana@upc.edu.pe.com",
            "ana upc@upc.edu.pe",
            "upc.edu.pe"
    })
    void constructor_withInvalidEmail_throwsDomainException(String value) {
        assertThatThrownBy(() -> new Email(value)).isInstanceOf(DomainException.class);
    }

    @Test
    void isValid_withNull_returnsFalse() {
        assertThat(Email.isValid(null)).isFalse();
    }

    @Test
    void twoEmailsWithTheSameValueAreEqual() {
        assertThat(new Email("ana@upc.edu.pe")).isEqualTo(new Email("ANA@upc.edu.pe"));
    }
}
