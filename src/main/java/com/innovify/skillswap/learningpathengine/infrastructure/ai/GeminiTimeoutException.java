package com.innovify.skillswap.learningpathengine.infrastructure.ai;

/** Gemini did not answer in time: either one attempt, or the whole time budget of the operation. */
public class GeminiTimeoutException extends GeminiUnavailableException {

    public GeminiTimeoutException(String message) {
        super(message, false);
    }
}
