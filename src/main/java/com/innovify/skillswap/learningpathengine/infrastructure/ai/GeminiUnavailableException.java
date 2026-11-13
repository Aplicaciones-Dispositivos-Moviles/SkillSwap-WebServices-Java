package com.innovify.skillswap.learningpathengine.infrastructure.ai;

/**
 * A Gemini model could not serve the request right now: it is overloaded, rate limited, failing, too slow or no
 * longer available. Another attempt or another model may work. Permanent errors (invalid key, bad request...)
 * are plain {@link IllegalStateException}s instead.
 */
public class GeminiUnavailableException extends IllegalStateException {

    private final boolean retryable;

    /**
     * @param retryable whether trying the same model again can help. A retired model (404) is unavailable but not
     *                  retryable.
     */
    public GeminiUnavailableException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public GeminiUnavailableException(String message) {
        this(message, true);
    }

    public boolean isRetryable() {
        return retryable;
    }
}
