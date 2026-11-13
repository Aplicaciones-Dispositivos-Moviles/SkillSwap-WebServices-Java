package com.innovify.skillswap.shared.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Origins from {@code cors.allowed-origins}, given as a list or a single comma-separated value, without
 * trailing slashes, blanks or duplicates.
 */
@ConfigurationProperties(prefix = "cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = normalize(allowedOrigins);
    }

    private static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String value : raw) {
            if (value == null) {
                continue;
            }
            for (String part : value.split(",")) {
                String origin = part.trim().replaceAll("/+$", "");
                if (!origin.isEmpty()) {
                    unique.putIfAbsent(origin.toLowerCase(Locale.ROOT), origin);
                }
            }
        }
        return List.copyOf(unique.values());
    }
}
