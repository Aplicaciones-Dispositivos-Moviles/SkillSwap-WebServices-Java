package com.innovify.skillswap.shared.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseDefaultsTest {

    @Test
    void withoutDatabaseUrl_hasNoDefaults() {
        assertThat(DatabaseDefaults.fromDatabaseUrl(null)).isEmpty();
        assertThat(DatabaseDefaults.fromDatabaseUrl("  ")).isEmpty();
    }

    @Test
    void withDatabaseUrl_setsTheDatasourceProperties() {
        var defaults = DatabaseDefaults.fromDatabaseUrl("postgres://skill_user:s3cret@dpg-abc123-a/skillswap_db");

        assertThat(defaults).containsKeys("spring.datasource.url", "spring.datasource.username",
                "spring.datasource.password");
        assertThat(defaults.get("spring.datasource.username")).isEqualTo("skill_user");
    }
}
