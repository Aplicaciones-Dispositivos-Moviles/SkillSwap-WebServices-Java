package com.innovify.skillswap.shared.infrastructure.persistence;

import java.util.Map;

/** Datasource properties derived from DATABASE_URL, to be used as the lowest-precedence defaults. */
public final class DatabaseDefaults {

    private DatabaseDefaults() {
    }

    public static Map<String, Object> fromDatabaseUrl(String databaseUrl) {
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return Map.of();
        }
        PostgresUrlConverter.JdbcConnection connection = PostgresUrlConverter.toJdbcConnection(databaseUrl);
        return Map.of(
                "spring.datasource.url", connection.url(),
                "spring.datasource.username", connection.username(),
                "spring.datasource.password", connection.password());
    }
}
