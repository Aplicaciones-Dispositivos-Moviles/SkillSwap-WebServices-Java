package com.innovify.skillswap.iam.application.eventhandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.innovify.skillswap.iam.application.fakes.FakeEmailSender;
import com.innovify.skillswap.iam.application.internal.emails.VerificationEmailComposer;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SendVerificationEmailEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final FakeEmailSender sender = new FakeEmailSender();
    private final SendVerificationEmailEventHandler handler = new SendVerificationEmailEventHandler(sender,
            new VerificationEmailComposer("https://api.skillswap.test"));

    private static EmailVerificationRequested event() {
        return new EmailVerificationRequested(7, "ana", "ana@upc.edu.pe", "the-token", NOW,
                NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void handle_sendsTheVerificationEmailWithTheLinkOfTheToken() {
        handler.handle(event());

        assertThat(sender.sent()).singleElement().satisfies(message -> {
            assertThat(message.toAddress()).isEqualTo("ana@upc.edu.pe");
            assertThat(message.textContent()).contains("verify-email?token=the-token", "24 horas");
        });
        assertThat(sender.lastTokenFor("ana@upc.edu.pe")).isEqualTo("the-token");
    }

    @Test
    void handle_whenTheProviderFails_onlyLogs() {
        sender.failNextSends(true);

        assertThatCode(() -> handler.handle(event())).doesNotThrowAnyException();
        assertThat(sender.sent()).isEmpty();
    }
}
