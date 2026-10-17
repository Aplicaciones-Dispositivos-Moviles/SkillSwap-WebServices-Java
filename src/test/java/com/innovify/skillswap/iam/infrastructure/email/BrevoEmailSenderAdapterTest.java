package com.innovify.skillswap.iam.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailDeliveryException;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import com.innovify.skillswap.iam.infrastructure.email.brevo.BrevoEmailSenderAdapter;
import com.innovify.skillswap.support.CapturedLogs;
import ch.qos.logback.classic.Level;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The adapter against a mock Brevo API: no network and no real key. */
class BrevoEmailSenderAdapterTest {

    private static final String BASE = "https://api.brevo.test/v3/";
    private static final String KEY = "xkeysib-test-key";

    private MockRestServiceServer server;
    private BrevoEmailSenderAdapter adapter;

    private static final EmailMessage MESSAGE = new EmailMessage("ana@upc.edu.pe", "ana", "Asunto", "Texto",
            "<p>HTML</p>");

    @BeforeEach
    void setUp() {
        EmailSettings settings = new EmailSettings(KEY, "no-reply@skillswap.test", "SkillSwap", BASE, 5, 10);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new BrevoEmailSenderAdapter(BrevoEmailSenderAdapter.configure(builder, settings).build(),
                settings.senderAddress(), settings.senderName());
    }

    @Test
    void send_postsTheTransactionalEmailWithTheApiKeyHeader() {
        server.expect(requestTo(BASE + "smtp/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("no-reply@skillswap.test"))
                .andExpect(jsonPath("$.sender.name").value("SkillSwap"))
                .andExpect(jsonPath("$.to[0].email").value("ana@upc.edu.pe"))
                .andExpect(jsonPath("$.to[0].name").value("ana"))
                .andExpect(jsonPath("$.subject").value("Asunto"))
                .andExpect(jsonPath("$.textContent").value("Texto"))
                .andExpect(jsonPath("$.htmlContent").value("<p>HTML</p>"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<id@smtp-relay.mailin.fr>\"}"));

        adapter.send(MESSAGE);

        server.verify();
    }

    @Test
    void send_whenBrevoRejectsTheEmail_throwsEmailDeliveryExceptionWithoutTheKey() {
        server.expect(requestTo(BASE + "smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\",\"message\":\"sender is not valid\"}"));

        assertThatThrownBy(() -> adapter.send(MESSAGE))
                .isInstanceOf(EmailDeliveryException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("sender is not valid")
                .hasMessageNotContaining(KEY);
    }

    @Test
    void send_whenBrevoFails_throwsEmailDeliveryException() {
        server.expect(requestTo(BASE + "smtp/email")).andRespond(withServerError());

        assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(EmailDeliveryException.class);
    }

    // ---------- Logs ----------

    @Test
    void send_logsTheAttemptAndTheAnswerOfBrevoWithTheRecipient() {
        server.expect(requestTo(BASE + "smtp/email"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"messageId\":\"<id@smtp-relay.mailin.fr>\"}"));

        try (CapturedLogs logs = CapturedLogs.of(BrevoEmailSenderAdapter.class)) {
            adapter.send(MESSAGE);

            assertThat(logs.messages(Level.INFO)).hasSize(2);
            assertThat(logs.messages(Level.INFO).get(0)).contains("ana@upc.edu.pe", "Asunto");
            assertThat(logs.messages(Level.INFO).get(1)).contains("ana@upc.edu.pe", "HTTP 201", "messageId");
            assertThat(logs.messages(Level.ERROR)).isEmpty();
        }
    }

    @Test
    void send_whenBrevoRejectsTheEmail_logsAnErrorWithTheStatusAndTheBodyButNeverTheKey() {
        server.expect(requestTo(BASE + "smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Key not found\",\"code\":\"unauthorized\"}"));

        try (CapturedLogs logs = CapturedLogs.of(BrevoEmailSenderAdapter.class)) {
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(EmailDeliveryException.class);

            assertThat(logs.messages(Level.ERROR)).singleElement().satisfies(error ->
                    assertThat(error).contains("ana@upc.edu.pe", "HTTP 401", "Key not found"));
            assertThat(logs.events()).noneMatch(event -> event.getFormattedMessage().contains(KEY));
        }
    }

    @Test
    void send_whenTheBodyIsVeryLong_logsOnlyItsBeginning() {
        server.expect(requestTo(BASE + "smtp/email"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("x".repeat(5_000)));

        try (CapturedLogs logs = CapturedLogs.of(BrevoEmailSenderAdapter.class)) {
            assertThatThrownBy(() -> adapter.send(MESSAGE)).isInstanceOf(EmailDeliveryException.class);

            assertThat(logs.messages(Level.ERROR).get(0)).hasSizeLessThan(700).endsWith("...");
        }
    }

    @Test
    void send_whenBrevoCannotBeReached_logsAnErrorAndThrowsEmailDeliveryException() {
        server.expect(requestTo(BASE + "smtp/email")).andRespond(withException(new IOException("connection reset")));

        try (CapturedLogs logs = CapturedLogs.of(BrevoEmailSenderAdapter.class)) {
            assertThatThrownBy(() -> adapter.send(MESSAGE))
                    .isInstanceOf(EmailDeliveryException.class)
                    .hasMessageContaining("could not be reached");

            assertThat(logs.messages(Level.ERROR)).singleElement().satisfies(error ->
                    assertThat(error).contains("ana@upc.edu.pe", "could not be reached"));
        }
    }
}
