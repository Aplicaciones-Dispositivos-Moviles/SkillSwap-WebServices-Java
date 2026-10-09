package com.innovify.skillswap.recognitionincentives.domain.model;

/** Errors of the Recognition &amp; Incentives context. The API code is the PascalCase name (WalletNotFound...). */
public enum RecognitionIncentivesError {
    NONE,
    INVALID_REDEMPTION_ITEM,
    WALLET_NOT_FOUND,
    NOT_WALLET_OWNER,
    INSUFFICIENT_BALANCE,
    OPERATION_CANCELLED,
    DATABASE_ERROR,
    INTERNAL_SERVER_ERROR
}
