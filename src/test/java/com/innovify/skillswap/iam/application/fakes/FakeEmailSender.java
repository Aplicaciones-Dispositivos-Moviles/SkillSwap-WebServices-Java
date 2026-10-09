package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailDeliveryException;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records the emails instead of sending them; it can be told to fail. */
public class FakeEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("verify-email\\?token=([A-Za-z0-9_%-]+)");

    private final List<EmailMessage> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    public List<EmailMessage> sent() {
        return sent;
    }

    public void failNextSends(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        sent.clear();
        failing = false;
    }

    /** The token of the verification link of the last email sent to the address. */
    public String lastTokenFor(String address) {
        for (int i = sent.size() - 1; i >= 0; i--) {
            EmailMessage message = sent.get(i);
            if (message.toAddress().equalsIgnoreCase(address)) {
                Matcher matcher = TOKEN.matcher(message.textContent());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        throw new AssertionError("No verification email was sent to " + address);
    }

    public long countTo(String address) {
        return sent.stream().filter(message -> message.toAddress().equalsIgnoreCase(address)).count();
    }

    @Override
    public void send(EmailMessage message) {
        if (failing) {
            throw new EmailDeliveryException("simulated failure");
        }
        sent.add(message);
    }
}
