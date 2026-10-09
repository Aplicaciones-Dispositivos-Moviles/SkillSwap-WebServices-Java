package com.innovify.skillswap.iam.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailDeliveryException;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import com.innovify.skillswap.iam.infrastructure.email.brevo.BrevoEmailSenderAdapter;
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

    @Test
    void mask_hidesMostOfTheAddress() {
        assertThat(BrevoEmailSenderAdapter.mask("ana@upc.edu.pe")).isEqualTo("a***@upc.edu.pe");
        assertThat(BrevoEmailSenderAdapter.mask("invalid")).isEqualTo("***");
    }
}
