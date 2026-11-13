package com.innovify.skillswap.credentialverification.domain;

import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialVerificationErrorTest {

    @Test
    void codes_areThePascalCaseNamesTheApiAlwaysUsed() {
        assertThat(java.util.Arrays.stream(CredentialVerificationError.values()).map(ErrorCodes::of))
                .containsExactly("None", "FileRequired", "InvalidFileType", "FileTooLarge", "DuplicateFile",
                        "FieldTooLong", "CertificateNotFound", "NotCertificateOwner", "InvalidStatusTransition",
                        "StorageError", "OperationCancelled", "DatabaseError", "InternalServerError");
    }
}
