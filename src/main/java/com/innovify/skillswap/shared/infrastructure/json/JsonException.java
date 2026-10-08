package com.innovify.skillswap.shared.infrastructure.json;

/** The text is not valid JSON, or the value cannot be written as JSON. */
public class JsonException extends RuntimeException {

    public JsonException(String message) {
        super(message);
    }
}
