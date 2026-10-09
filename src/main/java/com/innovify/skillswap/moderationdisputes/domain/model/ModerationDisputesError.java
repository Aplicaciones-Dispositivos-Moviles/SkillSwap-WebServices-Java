package com.innovify.skillswap.moderationdisputes.domain.model;

/** Errors of the Moderation &amp; Disputes context. The API code is the PascalCase name (DisputeNotFound...). */
public enum ModerationDisputesError {
    NONE,
    INVALID_OUTCOME,
    INVALID_DISPUTE_STATUS,
    COORDINATOR_NOTES_REQUIRED,
    COORDINATOR_NOTES_TOO_LONG,
    DISPUTE_NOT_FOUND,
    NOT_A_VERIFIER,
    NOT_ASSIGNED_REVIEWER,
    DISPUTE_ALREADY_RESOLVED,
    CERTIFICATE_NOT_SUSPICIOUS,
    OPERATION_CANCELLED,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
