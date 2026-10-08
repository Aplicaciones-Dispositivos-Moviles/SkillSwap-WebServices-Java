package com.innovify.skillswap.credentialverification.infrastructure.filestorage;

import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Cloudinary implementation of {@link FileStorageService}, talking to its REST API directly (no SDK). Files are
 * uploaded with the "authenticated" delivery type, so they have no public URL: the only way to read one is a
 * signed private-download URL that expires.
 *
 * <p>The storage reference is "{publicId}.{extension}", e.g. "certificates/7/3fa1....pdf". PDFs are stored as
 * Cloudinary "image" resources, like JPG and PNG.
 */
public class CloudinaryStorageService implements FileStorageService {

    private static final String DELIVERY_TYPE = "authenticated";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "application/pdf", "pdf");

    private final CloudinarySettings settings;
    private final HttpClient httpClient;
    private final Clock clock;

    public CloudinaryStorageService(CloudinarySettings settings, HttpClient httpClient, Clock clock) {
        this.settings = settings;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @Override
    public String upload(byte[] content, String contentType, String fileKey) {
        String extension = EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new IllegalArgumentException("Unsupported content type '" + contentType + "'.");
        }

        Map<String, String> parameters = new TreeMap<>();
        parameters.put("overwrite", "false");
        parameters.put("public_id", fileKey);
        parameters.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));
        parameters.put("type", DELIVERY_TYPE);
        parameters.put("unique_filename", "false");
        parameters.put("use_filename", "false");
        String signature = sign(parameters);

        String boundary = "----skillswap" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 1024);
        parameters.forEach((name, value) -> writeField(body, boundary, name, value));
        writeField(body, boundary, "api_key", settings.apiKey());
        writeField(body, boundary, "signature", signature);
        writeFile(body, boundary, fileKey, contentType, content);
        writeAscii(body, "--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder(endpoint("upload"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        send(request, "upload");

        return fileKey + "." + extension;
    }

    @Override
    public String getTemporaryUrl(String storageReference, Duration validFor) {
        Reference reference = Reference.parse(storageReference);
        long now = clock.instant().getEpochSecond();

        // Cloudinary "private download" URL: the signature covers the parameters sorted by name
        // (raw, unencoded values) followed by the API secret, hashed with SHA-1.
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("expires_at", String.valueOf(now + validFor.toSeconds()));
        parameters.put("format", reference.format());
        parameters.put("public_id", reference.publicId());
        parameters.put("timestamp", String.valueOf(now));
        parameters.put("type", DELIVERY_TYPE);

        StringJoiner query = new StringJoiner("&");
        parameters.forEach((name, value) -> query.add(name + "=" + encode(value)));
        query.add("api_key=" + encode(settings.apiKey()));
        query.add("signature=" + sign(parameters));
        return endpoint("download") + "?" + query;
    }

    @Override
    public void delete(String storageReference) {
        Reference reference = Reference.parse(storageReference);

        Map<String, String> parameters = new TreeMap<>();
        parameters.put("invalidate", "true");
        parameters.put("public_id", reference.publicId());
        parameters.put("timestamp", String.valueOf(clock.instant().getEpochSecond()));
        parameters.put("type", DELIVERY_TYPE);
        String signature = sign(parameters);

        StringJoiner form = new StringJoiner("&");
        parameters.forEach((name, value) -> form.add(name + "=" + encode(value)));
        form.add("api_key=" + encode(settings.apiKey()));
        form.add("signature=" + signature);

        HttpRequest request = HttpRequest.newBuilder(endpoint("destroy"))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.toString()))
                .build();
        send(request, "delete");
    }

    private URI endpoint(String action) {
        return URI.create("%s/v1_1/%s/image/%s".formatted(settings.apiBaseUrl(), settings.cloudName(), action));
    }

    private void send(HttpRequest request, String operation) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cloudinary " + operation + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Cloudinary " + operation + " was interrupted.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String body = response.body() == null ? "" : response.body();
            throw new IllegalStateException("Cloudinary %s failed with HTTP %d: %s".formatted(
                    operation, response.statusCode(), body.length() > 300 ? body.substring(0, 300) : body));
        }
    }

    /** SHA-1 of the sorted {@code name=value} pairs joined with "&" followed by the API secret. */
    private String sign(Map<String, String> sortedParameters) {
        StringJoiner toSign = new StringJoiner("&");
        sortedParameters.forEach((name, value) -> toSign.add(name + "=" + value));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest((toSign + settings.apiSecret()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is not available", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) {
        writeAscii(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
        writeAscii(out, "\r\n");
    }

    private static void writeFile(ByteArrayOutputStream out, String boundary, String fileName,
                                  String contentType, byte[] content) {
        writeAscii(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\""
                + fileName.replace("\"", "") + "\"\r\nContent-Type: " + contentType + "\r\n\r\n");
        out.writeBytes(content);
        writeAscii(out, "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private record Reference(String publicId, String format) {

        static Reference parse(String storageReference) {
            int dot = storageReference == null ? -1 : storageReference.lastIndexOf('.');
            if (dot <= 0 || dot == storageReference.length() - 1) {
                throw new IllegalArgumentException("Invalid storage reference '" + storageReference + "'.");
            }
            return new Reference(storageReference.substring(0, dot), storageReference.substring(dot + 1));
        }
    }
}
