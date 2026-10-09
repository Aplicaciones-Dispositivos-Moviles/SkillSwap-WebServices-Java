package com.innovify.skillswap.reputation.domain.model;

/** Errors of the Reputation context. The API code is the PascalCase name (ReputationNotFound...). */
public enum ReputationError {
    NONE,
    REPUTATION_NOT_FOUND,
    NOT_REPUTATION_OWNER,
    OPERATION_CANCELLED,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
