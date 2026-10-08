package com.innovify.skillswap.credentialverification.infrastructure.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Runs the real HTTP calls against a local fake Cloudinary ({@link HttpServer}), so no network and no
 * credentials are needed.
 */
class CloudinaryStorageServiceTest {

    private static final String SECRET = "secret456";
    private static final Instant NOW = Instant.parse("2026-10-08T12:00:00Z");

    /** What the fake server received. */
    private record Received(String method, String path, String contentType, byte[] body) {
        String bodyText() {
            return new String(body, StandardCharsets.ISO_8859_1);
        }
    }

    private HttpServer server;
    private final List<Received> requests = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 200;
    private volatile String responseBody = "{\"public_id\":\"x\"}";

    private CloudinaryStorageService service;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.add(new Received(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Content-Type"), exchange.getRequestBody().readAllBytes()));
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(responseStatus, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        service = create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private static CloudinaryStorageService create(String baseUrl) {
        return new CloudinaryStorageService(new CloudinarySettings("demo", "key123", SECRET, baseUrl),
                HttpClient.newHttpClient(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String sha1(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Signature expected by Cloudinary: sorted "name=value" pairs joined by "&" plus the secret, in SHA-1. */
    private static String expectedSignature(Map<String, String> parameters) {
        StringBuilder toSign = new StringBuilder();
        new TreeMap<>(parameters).forEach((name, value) -> {
            if (!toSign.isEmpty()) {
                toSign.append('&');
            }
            toSign.append(name).append('=').append(value);
        });
        return sha1(toSign + SECRET);
    }

    private static Map<String, String> multipartFields(Received request) {
        String boundary = request.contentType().substring(request.contentType().indexOf("boundary=") + 9);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String part : request.bodyText().split("--" + boundary)) {
            int nameStart = part.indexOf("name=\"");
            if (nameStart < 0) {
                continue;
            }
            String name = part.substring(nameStart + 6, part.indexOf('"', nameStart + 6));
            int valueStart = part.indexOf("\r\n\r\n");
            String value = part.substring(valueStart + 4, part.lastIndexOf("\r\n"));
            fields.put(name, value);
        }
        return fields;
    }

    private static Map<String, String> formFields(Received request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : request.bodyText().split("&")) {
            int equals = pair.indexOf('=');
            fields.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return fields;
    }

    private static Map<String, String> queryFields(String url) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            int equals = pair.indexOf('=');
            fields.put(pair.substring(0, equals), URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
        }
        return fields;
    }

    // ---------- Upload ----------

    @Test
    void upload_sendsASignedAuthenticatedMultipartRequestAndReturnsTheReference() {
        byte[] content = {1, 2, 3, 4, 5};

        String reference = service.upload(content, "application/pdf", "certificates/7/abc");

        assertThat(reference).isEqualTo("certificates/7/abc.pdf");
        assertThat(requests).hasSize(1);
        Received request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1_1/demo/image/upload");
        assertThat(request.contentType()).startsWith("multipart/form-data; boundary=");

        Map<String, String> fields = multipartFields(request);
        assertThat(fields).containsEntry("public_id", "certificates/7/abc")
                .containsEntry("type", "authenticated")
                .containsEntry("overwrite", "false")
                .containsEntry("unique_filename", "false")
                .containsEntry("use_filename", "false")
                .containsEntry("timestamp", String.valueOf(NOW.getEpochSecond()))
                .containsEntry("api_key", "key123")
                .containsKey("file");
        assertThat(fields.get("file")).isEqualTo(new String(content, StandardCharsets.ISO_8859_1));
    }

    @Test
    void upload_signsTheParametersAndNeverSendsTheSecret() {
        service.upload(new byte[]{1}, "image/png", "certificates/7/abc");

        Received request = requests.get(0);
        Map<String, String> fields = multipartFields(request);
        Map<String, String> signed = new TreeMap<>(fields);
        signed.keySet().removeAll(List.of("file", "api_key", "signature"));

        assertThat(fields.get("signature")).isEqualTo(expectedSignature(signed));
        assertThat(request.bodyText()).doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"image/jpeg:jpg", "image/png:png", "application/pdf:pdf"})
    void upload_returnsTheExtensionOfTheContentType(String pair) {
        String[] parts = pair.split(":");

        assertThat(service.upload(new byte[]{1}, parts[0], "certificates/1/x")).isEqualTo("certificates/1/x." + parts[1]);
    }

    @Test
    void upload_withAnUnsupportedContentType_throwsBeforeAnyNetworkCall() {
        assertThatThrownBy(() -> service.upload(new byte[]{1, 2, 3}, "text/plain", "certificates/1/x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }

    @Test
    void upload_whenCloudinaryRejectsIt_throwsWithTheStatusAndMessage() {
        responseStatus = 401;
        responseBody = "{\"error\":{\"message\":\"Invalid Signature\"}}";

        assertThatThrownBy(() -> service.upload(new byte[]{1}, "image/png", "certificates/1/x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("Invalid Signature");
    }

    @Test
    void upload_whenCloudinaryIsUnreachable_throws() {
        server.stop(0);

        assertThatThrownBy(() -> service.upload(new byte[]{1}, "image/png", "certificates/1/x"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Delete ----------

    @Test
    void delete_sendsASignedDestroyRequestForTheAuthenticatedResource() {
        service.delete("certificates/7/abc.pdf");

        assertThat(requests).hasSize(1);
        Received request = requests.get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1_1/demo/image/destroy");
        assertThat(request.contentType()).isEqualTo("application/x-www-form-urlencoded");

        Map<String, String> fields = formFields(request);
        assertThat(fields).containsEntry("public_id", "certificates/7/abc")
                .containsEntry("type", "authenticated")
                .containsEntry("invalidate", "true")
                .containsEntry("api_key", "key123");

        Map<String, String> signed = new TreeMap<>(fields);
        signed.keySet().removeAll(List.of("api_key", "signature"));
        assertThat(fields.get("signature")).isEqualTo(expectedSignature(signed));
        assertThat(request.bodyText()).doesNotContain(SECRET);
    }

    @Test
    void delete_whenCloudinaryFails_throws() {
        responseStatus = 500;
        responseBody = "boom";

        assertThatThrownBy(() -> service.delete("certificates/7/abc.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

    // ---------- Temporary URL ----------

    @Test
    void getTemporaryUrl_buildsASignedPrivateDownloadUrl() {
        CloudinaryStorageService real = create("https://api.cloudinary.com");

        String url = real.getTemporaryUrl("certificates/7/abc.pdf", Duration.ofMinutes(15));

        URI uri = URI.create(url);
        assertThat(uri.getHost()).isEqualTo("api.cloudinary.com");
        assertThat(uri.getPath()).isEqualTo("/v1_1/demo/image/download");

        Map<String, String> query = queryFields(url);
        assertThat(query).containsEntry("public_id", "certificates/7/abc")
                .containsEntry("format", "pdf")
                .containsEntry("type", "authenticated")
                .containsEntry("api_key", "key123")
                .containsEntry("timestamp", String.valueOf(NOW.getEpochSecond()))
                .containsEntry("expires_at", String.valueOf(NOW.plus(Duration.ofMinutes(15)).getEpochSecond()));
    }

    @Test
    void getTemporaryUrl_signsTheParametersAndNeverExposesTheSecret() {
        String url = service.getTemporaryUrl("certificates/7/abc.pdf", Duration.ofMinutes(15));

        Map<String, String> query = queryFields(url);
        Map<String, String> signed = new TreeMap<>(query);
        signed.keySet().removeAll(List.of("api_key", "signature"));

        assertThat(query.get("signature")).isEqualTo(expectedSignature(signed));
        assertThat(url).doesNotContain(SECRET);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-extension", ".hidden", "trailing."})
    void getTemporaryUrl_withAnInvalidReference_throws(String reference) {
        assertThatThrownBy(() -> service.getTemporaryUrl(reference, Duration.ofMinutes(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-extension", ".hidden", "trailing."})
    void delete_withAnInvalidReference_throwsBeforeAnyNetworkCall(String reference) {
        assertThatThrownBy(() -> service.delete(reference)).isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).isEmpty();
    }
}
