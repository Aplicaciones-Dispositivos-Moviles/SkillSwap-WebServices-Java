package com.innovify.skillswap.support;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base class of the tests that run the whole application against a real PostgreSQL, started by Testcontainers
 * through its JDBC URL (jdbc:tc:...). The schema comes from iam-test-schema.sql and
 * credential-verification-test-schema.sql, copies of what the C# API's migrations create, and Hibernate only
 * validates it. All the subclasses share one Spring context and one container. They are skipped when Docker is
 * not available.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///skillswap",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:iam-test-schema.sql,"
                + "classpath:credential-verification-test-schema.sql",
        "token.settings.secret=test-secret-with-at-least-32-characters-long!",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-key",
        "cloudinary.api-secret=test-secret"
})
@ExtendWith(DockerAvailableCondition.class)
public abstract class PostgresIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    protected void cleanDatabase() throws SQLException {
        execute("TRUNCATE TABLE users, certificates RESTART IDENTITY");
    }

    protected void execute(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    protected String queryString(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }
}
