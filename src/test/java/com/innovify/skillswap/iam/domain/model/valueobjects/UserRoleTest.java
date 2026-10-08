package com.innovify.skillswap.iam.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void value_isTheRepresentationStoredInTheDatabase() {
        assertThat(UserRole.STUDENT.value()).isEqualTo("Student");
        assertThat(UserRole.COORDINATOR.value()).isEqualTo("Coordinator");
    }

    @Test
    void fromValue_isCaseInsensitive() {
        assertThat(UserRole.fromValue("Student")).isEqualTo(UserRole.STUDENT);
        assertThat(UserRole.fromValue("coordinator")).isEqualTo(UserRole.COORDINATOR);
    }

    @Test
    void fromValue_withAnUnknownRole_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> UserRole.fromValue("Verifier")).isInstanceOf(IllegalArgumentException.class);
    }
}
