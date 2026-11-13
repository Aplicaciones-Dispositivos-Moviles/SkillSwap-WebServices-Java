package com.innovify.skillswap.shared.domain.exceptions;

/** Thrown when a domain invariant is violated (e.g. an invalid Value Object). */
public class DomainException extends RuntimeException {

    public DomainException(String message) {
        super(message);
    }
}
