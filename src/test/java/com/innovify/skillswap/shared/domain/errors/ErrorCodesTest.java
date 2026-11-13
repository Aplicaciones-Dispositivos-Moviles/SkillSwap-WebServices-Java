package com.innovify.skillswap.shared.domain.errors;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ErrorCodesTest {

    private enum Sample { NONE, USER_NOT_FOUND }

    @Test
    void of_convertsTheConstantNameToPascalCase() {
        assertThat(ErrorCodes.of(Sample.NONE)).isEqualTo("None");
        assertThat(ErrorCodes.of(Sample.USER_NOT_FOUND)).isEqualTo("UserNotFound");
    }
}
