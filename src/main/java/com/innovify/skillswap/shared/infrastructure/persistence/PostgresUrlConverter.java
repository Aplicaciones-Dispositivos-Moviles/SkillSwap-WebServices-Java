package com.innovify.skillswap.shared.infrastructure.persistence;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Converts the connection URL that hosting platforms such as Render provide
 * (postgres://user:password@host:port/database) into a JDBC URL plus credentials.
 */
public final class PostgresUrlConverter {

    public static final int DEFAULT_PORT = 5432;
    private static final String EXPECTED = "The database URL must look like postgres://user:password@host:port/database.";

    private PostgresUrlConverter() {
    }

    public record JdbcConnection(String url, String username, String password) {
    }

    /**
     * SSL is required for external hosts (the name has dots) and preferred for internal ones, which are
     * reached through the platform's private network by a short name. The server certificate is not validated.
     *
     * @throws IllegalArgumentException when the URL is not a valid PostgreSQL URL
     */
    public static JdbcConnection toJdbcConnection(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            throw invalid();
        }
        URI uri;
        try {
            uri = new URI(databaseUrl.trim());
        } catch (URISyntaxException exception) {
            throw invalid();
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !(scheme.equals("postgres") || scheme.equals("postgresql"))
                || host == null || host.isEmpty()) {
            throw invalid();
        }

        String userInfo = uri.getRawUserInfo();
        if (userInfo == null) {
            throw invalid();
        }
        int separator = userInfo.indexOf(':');
        if (separator <= 0 || separator == userInfo.length() - 1) {
            throw invalid();
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        String database = path.startsWith("/") ? path.substring(1) : path;
        if (database.isEmpty()) {
            throw invalid();
        }

        int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PORT;
        String sslMode = host.contains(".") ? "require" : "prefer";
        String url = "jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?sslmode=" + sslMode + "&sslfactory=org.postgresql.ssl.NonValidatingFactory";

        return new JdbcConnection(url,
                decode(userInfo.substring(0, separator)),
                decode(userInfo.substring(separator + 1)));
    }

    // A literal '+' is part of the value, it is not a space.
    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException(EXPECTED);
    }
}
