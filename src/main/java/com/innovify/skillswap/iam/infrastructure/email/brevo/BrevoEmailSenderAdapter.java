package com.innovify.skillswap.iam.infrastructure.email.brevo;

import com.innovify.skillswap.iam.application.internal.outboundservices.EmailDeliveryException;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailMessage;
import com.innovify.skillswap.iam.application.internal.outboundservices.EmailSender;
import com.innovify.skillswap.iam.infrastructure.email.EmailSettings;
import com.innovify.skillswap.shared.infrastructure.json.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link EmailSender} on top of the Brevo transactional email API ({@code POST /v3/smtp/email}). It works over
 * HTTPS, so it is not affected by the SMTP ports that Render's free plan blocks. The API key is a header of the
 * client and is never logged.
 */
public class BrevoEmailSenderAdapter implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailSenderAdapter.class);

    static final String SEND_PATH = "smtp/email";

    private final RestClient restClient;
    private final String senderAddress;
    private final String senderName;

    public BrevoEmailSenderAdapter(RestClient restClient, String senderAddress, String senderName) {
        this.restClient = restClient;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
    }

    /**
     * Sets the base URL and the authentication of the client. The request factory (with the timeouts) is left to
     * the caller, so a test can bind a mock server to the same builder.
     */
    public static RestClient.Builder configure(RestClient.Builder builder, EmailSettings settings) {
        return builder
                .baseUrl(settings.brevoBaseUrl())
                .defaultHeader("api-key", settings.brevoApiKey())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    @Override
    public void send(EmailMessage message) {
        try {
            restClient.post()
                    .uri(SEND_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Json.write(toRequest(message)))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Email '{}' sent through Brevo to {}", message.subject(), mask(message.toAddress()));
        } catch (RestClientResponseException exception) {
            throw new EmailDeliveryException("Brevo rejected the email (HTTP %d): %s".formatted(
                    exception.getStatusCode().value(), exception.getResponseBodyAsString()), exception);
        } catch (RestClientException exception) {
            throw new EmailDeliveryException("Brevo could not be reached: " + exception.getMessage(), exception);
        }
    }

    Map<String, Object> toRequest(EmailMessage message) {
        Map<String, Object> recipient = new LinkedHashMap<>();
        recipient.put("email", message.toAddress());
        if (message.toName() != null && !message.toName().isBlank()) {
            recipient.put("name", message.toName());
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("sender", Map.of("name", senderName, "email", senderAddress));
        request.put("to", List.of(recipient));
        request.put("subject", message.subject());
        request.put("htmlContent", message.htmlContent());
        request.put("textContent", message.textContent());
        return request;
    }

    /** "a***@upc.edu.pe": enough to follow a delivery in the logs without exposing the whole address. */
    public static String mask(String address) {
        int at = address.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return address.charAt(0) + "***" + address.substring(at);
    }
}
