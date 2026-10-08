package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import org.springframework.context.support.ResourceBundleMessageSource;

public final class TestMessages {

    private TestMessages() {
    }

    /** The real message bundles, so the tests also notice a missing key. */
    public static ResourceBundleMessageSource source() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        return messages;
    }
}
