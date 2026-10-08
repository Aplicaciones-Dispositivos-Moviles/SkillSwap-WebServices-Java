package com.innovify.skillswap.shared.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Outcome of an application service call: either a value or a domain error with a localized message. */
public final class Result<T> {

    private final boolean success;
    private final T value;
    private final String message;
    private final Enum<?> error;
    private final Map<String, Object> details;

    private Result(boolean success, T value, String message, Enum<?> error, Map<String, Object> details) {
        this.success = success;
        this.value = value;
        this.message = message;
        this.error = error;
        this.details = details;
    }

    public static <T> Result<T> success(T value) {
        return new Result<>(true, value, "", null, null);
    }

    public static Result<Void> success() {
        return new Result<>(true, null, "", null, null);
    }

    public static <T> Result<T> failure(Enum<?> error, String message) {
        return new Result<>(false, null, message, error, null);
    }

    /** {@code details} is optional structured data about the failure, exposed to clients as extra members. */
    public static <T> Result<T> failure(Enum<?> error, String message, Map<String, Object> details) {
        return new Result<>(false, null, message, error,
                Collections.unmodifiableMap(new LinkedHashMap<>(details)));
    }

    public boolean isSuccess() {
        return success;
    }

    public boolean isFailure() {
        return !success;
    }

    public T value() {
        return value;
    }

    public String message() {
        return message;
    }

    public Enum<?> error() {
        return error;
    }

    public Map<String, Object> details() {
        return details;
    }
}
