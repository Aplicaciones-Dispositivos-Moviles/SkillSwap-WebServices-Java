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
import org.springframework.context.annotation.Import;

/**
 * Base class of the tests that run the whole application against a real PostgreSQL, started by Testcontainers
 * through its JDBC URL (jdbc:tc:...). The schema is created by the same Flyway migrations as production
 * (db/migration), and Hibernate only validates it, so a mapping that drifts from the migrations fails the tests.
 * The file storage, the question generator, the payment gateway and the email sender are in-memory fakes. All the subclasses share
 * one Spring context and one container. They are skipped when Docker is not available.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:tc:postgresql:16-alpine:///skillswap?stringtype=unspecified",
        "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
        "spring.jpa.hibernate.ddl-auto=validate",
        "token.settings.secret=test-secret-with-at-least-32-characters-long!",
        "cloudinary.cloud-name=test-cloud",
        "cloudinary.api-key=test-key",
        "cloudinary.api-secret=test-secret",
        "gemini.api-key=test-gemini-key",
        "revenuecat.webhook-authorization=" + PostgresIntegrationTest.WEBHOOK_AUTHORIZATION,
        "billing.expiration-check-enabled=false"
})
@Import({FileStorageTestConfig.class, QuestionGenerationTestConfig.class, PaymentGatewayTestConfig.class,
        EmailSenderTestConfig.class})
@ExtendWith(DockerAvailableCondition.class)
public abstract class PostgresIntegrationTest {

    /** The Authorization header the RevenueCat webhook expects in the tests. */
    protected static final String WEBHOOK_AUTHORIZATION = "Bearer test-webhook-secret";

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    protected void cleanDatabase() throws SQLException {
        execute("TRUNCATE TABLE users, certificates, path_nodes, learning_paths, assessment_blueprints, "
                + "assessment_attempts, verification_cases, verifier_profiles, verifier_reliabilities, student_employability_scores, wallets, credit_transactions, "
                + "subscriptions, processed_webhook_events RESTART IDENTITY");
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
