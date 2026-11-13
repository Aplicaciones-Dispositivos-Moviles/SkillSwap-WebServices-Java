package com.innovify.skillswap.shared.interfaces.rest;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns the framework's own errors (invalid body, malformed JSON, unsupported media type...) into
 * {@code ProblemDetail} responses, like the rest of the API's errors.
 */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
}
