package com.innovify.skillswap.iam.domain.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DefaultEmailDomainValidatorTest {

    private final EmailDomainValidator validator = new DefaultEmailDomainValidator();

    @Test
    void isInstitutionalDomain_withAnEduPeEmail_returnsTrue() {
        assertThat(validator.isInstitutionalDomain("ana@upc.edu.pe")).isTrue();
    }

    @Test
    void isInstitutionalDomain_withAnyOtherEmail_returnsFalse() {
        assertThat(validator.isInstitutionalDomain("ana@gmail.com")).isFalse();
        assertThat(validator.isInstitutionalDomain("  ")).isFalse();
        assertThat(validator.isInstitutionalDomain(null)).isFalse();
    }
}
