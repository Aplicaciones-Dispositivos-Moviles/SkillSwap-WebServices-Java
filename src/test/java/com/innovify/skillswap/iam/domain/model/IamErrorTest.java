package com.innovify.skillswap.iam.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The codes are part of the API contract (title of the error responses): they must match the C# names. */
class IamErrorTest {

    @Test
    void codes_matchTheNamesOfTheCSharpApi() {
        assertThat(Arrays.stream(IamError.values()).map(ErrorCodes::of)).containsExactly(
                "None", "InvalidCredentials", "UserBanned", "UsernameAlreadyTaken", "EmailAlreadyTaken",
                "InvalidInstitutionalEmail", "InvalidUsername", "WeakPassword", "UserNotFound",
                "NotProfileOwner", "BioTooLong", "InvalidFullName", "OperationCancelled", "DatabaseError", "InternalServerError");
    }
}
