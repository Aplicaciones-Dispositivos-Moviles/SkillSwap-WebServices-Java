package com.innovify.skillswap.recognitionincentives.domain.model.queries;

/** Anyone with the token of a credential can see it. */
public record GetPublicCredentialByTokenQuery(String token) {
}
