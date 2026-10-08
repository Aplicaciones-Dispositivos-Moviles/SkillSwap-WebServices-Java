package com.innovify.skillswap.credentialverification.domain.model;

/** Errors of the Credential Verification context. The API code is the PascalCase name (FileRequired...). */
public enum CredentialVerificationError {
    NONE,
    FILE_REQUIRED,
    INVALID_FILE_TYPE,
    FILE_TOO_LARGE,
    DUPLICATE_FILE,
    FIELD_TOO_LONG,
    CERTIFICATE_NOT_FOUND,
    NOT_CERTIFICATE_OWNER,
    INVALID_STATUS_TRANSITION,
    STORAGE_ERROR,
    OPERATION_CANCELLED,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
