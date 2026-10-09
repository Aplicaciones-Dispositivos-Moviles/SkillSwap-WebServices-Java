package com.innovify.skillswap.iam.application.internal.outboundservices;

import java.util.Objects;

/**
 * A transactional email.
 *
 * @param toAddress   the recipient address
 * @param toName      the recipient name shown by the mail client
 * @param subject     the subject
 * @param textContent the plain-text version
 * @param htmlContent the HTML version
 */
public record EmailMessage(String toAddress, String toName, String subject, String textContent,
                           String htmlContent) {

    public EmailMessage {
        Objects.requireNonNull(toAddress, "toAddress");
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(textContent, "textContent");
        Objects.requireNonNull(htmlContent, "htmlContent");
    }
}
