package com.innovify.skillswap.iam.domain.model.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserRoleTest {

    @Test
    void value_isTheRepresentationStoredInTheDatabase() {
        assertThat(UserRole.STUDENT.value()).isEqualTo("Student");
    }

    @Test
    void fromValue_isCaseInsensitive() {
        assertThat(UserRole.fromValue("Student")).isEqualTo(UserRole.STUDENT);
        assertThat(UserRole.fromValue("student")).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void fromValue_withAnUnknownRole_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> UserRole.fromValue("Verifier")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserRole.fromValue("Coordinator")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void student_isTheOnlyRole() {
        assertThat(UserRole.values()).containsExactly(UserRole.STUDENT);
    }
}
