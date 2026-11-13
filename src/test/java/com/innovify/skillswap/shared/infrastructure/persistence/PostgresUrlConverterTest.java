package com.innovify.skillswap.shared.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.infrastructure.persistence.PostgresUrlConverter.JdbcConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PostgresUrlConverterTest {

    @Test
    void withAnExternalUrl_keepsEveryPartAndRequiresSsl() {
        JdbcConnection connection = PostgresUrlConverter.toJdbcConnection(
                "postgresql://skill_user:s3cret@dpg-abc123-a.virginia-postgres.render.com:5433/skillswap_db");

        assertThat(connection.url()).startsWith(
                "jdbc:postgresql://dpg-abc123-a.virginia-postgres.render.com:5433/skillswap_db?");
        assertThat(connection.url()).contains("sslmode=require");
        assertThat(connection.username()).isEqualTo("skill_user");
        assertThat(connection.password()).isEqualTo("s3cret");
    }

    @Test
    void withAnInternalUrl_usesTheDefaultPortAndPrefersSsl() {
        JdbcConnection connection = PostgresUrlConverter.toJdbcConnection(
                "postgres://skill_user:s3cret@dpg-abc123-a/skillswap_db");

        assertThat(connection.url()).startsWith("jdbc:postgresql://dpg-abc123-a:5432/skillswap_db?");
        assertThat(connection.url()).contains("sslmode=prefer");
    }

    @Test
    void decodesSpecialCharactersOfTheCredentials() {
        JdbcConnection connection = PostgresUrlConverter.toJdbcConnection(
                "postgres://sk%40ll:p%40ss%3Aword%2F1@dpg-abc123-a/skillswap_db");

        assertThat(connection.username()).isEqualTo("sk@ll");
        assertThat(connection.password()).isEqualTo("p@ss:word/1");
    }

    @Test
    void keepsALiteralPlusInThePassword() {
        JdbcConnection connection = PostgresUrlConverter.toJdbcConnection(
                "postgres://skill_user:a+b@dpg-abc123-a/skillswap_db");

        assertThat(connection.password()).isEqualTo("a+b");
    }

    @Test
    void ignoresTheQueryString() {
        JdbcConnection connection = PostgresUrlConverter.toJdbcConnection(
                "postgres://skill_user:s3cret@dpg-abc123-a/skillswap_db?sslmode=require");

        assertThat(connection.url()).contains("/skillswap_db?sslmode=prefer");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "not a url",
            "mysql://user:pass@host/db",
            "postgres://host/db",
            "postgres://user@host/db",
            "postgres://user:pass@host",
            "postgres://user:pass@host/"
    })
    void withAnInvalidUrl_throwsIllegalArgumentException(String url) {
        assertThatThrownBy(() -> PostgresUrlConverter.toJdbcConnection(url))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
