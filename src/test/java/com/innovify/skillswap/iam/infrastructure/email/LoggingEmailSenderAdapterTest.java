package com.innovify.skillswap.iam.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import com.innovify.skillswap.iam.infrastructure.email.logging.LoggingEmailSenderAdapter;
import com.innovify.skillswap.support.CapturedLogs;
import org.junit.jupiter.api.Test;

class LoggingEmailSenderAdapterTest {

    @Test
    void send_writesTheRecipientTheSubjectAndTheLinkToTheLog() {
        EmailMessage message = new EmailMessage("ana@upc.edu.pe", "ana", "Verifica tu cuenta",
                "Abre https://skillswap.test/api/v1/authentication/verify-email?token=abc", "<p>HTML</p>");

        try (CapturedLogs logs = CapturedLogs.of(LoggingEmailSenderAdapter.class)) {
            new LoggingEmailSenderAdapter().send(message);

            assertThat(logs.messages(Level.INFO)).singleElement().satisfies(line -> {
                assertThat(line).contains("NOT sent", "ana@upc.edu.pe", "Verifica tu cuenta", "verify-email?token=abc");
                assertThat(line).doesNotContain("{}");
            });
        }
    }
}
