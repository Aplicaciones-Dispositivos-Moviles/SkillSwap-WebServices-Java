package com.innovify.skillswap.iam.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.innovify.skillswap.iam.application.fakes.FakeEmailSender;
import com.innovify.skillswap.iam.application.internal.commandservices.EmailVerificationIssuer;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * US01 and US02 end to end: the sign-up sends the verification email after the account is committed, an unverified
 * account cannot sign in (and gets the email again), and the link of the email verifies it. Real security filter,
 * real services and PostgreSQL; only the email provider is a recording fake.
 */
class EmailVerificationIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FakeEmailSender emailSender;

    @Autowired
    private DataSource dataSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        emailSender.clear();
    }

    private ResultActions signUp(String username, String email) throws Exception {
        return mockMvc.perform(post("/api/v1/authentication/sign-up").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"email\":\"" + email
                        + "\",\"password\":\"password123\"}"));
    }

    private ResultActions signIn(String username) throws Exception {
        return mockMvc.perform(post("/api/v1/authentication/sign-in").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"));
    }

    @Test
    void signUp_sendsTheVerificationEmailToTheInstitutionalAddressAndStoresOnlyTheHash() throws Exception {
        signUp("ana", "Ana@UPC.edu.pe")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isVerified").value(false));

        assertThat(emailSender.sent()).singleElement().satisfies(message -> {
            assertThat(message.toAddress()).isEqualTo("ana@upc.edu.pe");
            assertThat(message.subject()).isEqualTo("Verifica tu correo institucional en SkillSwap");
            assertThat(message.textContent()).contains("/api/v1/authentication/verify-email?token=");
        });
        String token = emailSender.lastTokenFor("ana@upc.edu.pe");
        assertThat(queryString("SELECT verification_token_hash FROM users WHERE username = 'ana'"))
                .isEqualTo(EmailVerificationIssuer.hash(token))
                .isNotEqualTo(token);
        assertThat(queryString("SELECT is_verified FROM users WHERE username = 'ana'")).isEqualTo("f");
    }

    @Test
    void anUnverifiedAccount_cannotSignInUntilTheLinkOfTheEmailIsOpened() throws Exception {
        signUp("ana", "ana@upc.edu.pe").andExpect(status().isCreated());

        signIn("ana")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("EmailNotVerified"));

        mockMvc.perform(post("/api/v1/authentication/verify-email").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + emailSender.lastTokenFor("ana@upc.edu.pe") + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified").value(true));

        signIn("ana")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isVerified").value(true))
                .andExpect(jsonPath("$.token").isNotEmpty());
        assertThat(queryString("SELECT verification_token_hash FROM users WHERE username = 'ana'")).isNull();
    }

    @Test
    void signIn_ofAnUnverifiedAccount_sendsTheEmailAgainOnceTheCooldownPassed() throws Exception {
        signUp("ana", "ana@upc.edu.pe").andExpect(status().isCreated());
        String firstToken = emailSender.lastTokenFor("ana@upc.edu.pe");

        signIn("ana").andExpect(status().isForbidden());
        assertThat(emailSender.countTo("ana@upc.edu.pe")).isEqualTo(1);

        // The last email was sent more than the cooldown ago.
        execute("UPDATE users SET verification_email_sent_at = now() - interval '10 minutes'");
        signIn("ana").andExpect(status().isForbidden());

        assertThat(emailSender.countTo("ana@upc.edu.pe")).isEqualTo(2);
        String newToken = emailSender.lastTokenFor("ana@upc.edu.pe");
        assertThat(newToken).isNotEqualTo(firstToken);
        mockMvc.perform(get("/api/v1/authentication/verify-email").param("token", firstToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/authentication/verify-email").param("token", newToken))
                .andExpect(status().isOk());
    }

    @Test
    void verifyEmail_withAnExpiredLink_answers410() throws Exception {
        signUp("ana", "ana@upc.edu.pe").andExpect(status().isCreated());
        execute("UPDATE users SET verification_token_expires_at = now() - interval '1 minute'");

        mockMvc.perform(get("/api/v1/authentication/verify-email")
                        .param("token", emailSender.lastTokenFor("ana@upc.edu.pe")))
                .andExpect(status().isGone());
        assertThat(queryString("SELECT is_verified FROM users WHERE username = 'ana'")).isEqualTo("f");
    }

    @Test
    void resendVerification_isAnonymousAndAnswersTheSameForAnyEmail() throws Exception {
        signUp("ana", "ana@upc.edu.pe").andExpect(status().isCreated());
        execute("UPDATE users SET verification_email_sent_at = now() - interval '10 minutes'");

        String registered = mockMvc.perform(post("/api/v1/authentication/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"ana@upc.edu.pe\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/v1/authentication/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nobody@upc.edu.pe\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        assertThat(registered).isEqualTo(unknown);
        assertThat(emailSender.countTo("ana@upc.edu.pe")).isEqualTo(2);
        assertThat(emailSender.countTo("nobody@upc.edu.pe")).isZero();
    }

    /**
     * V6 marks every account created before it as verified, so the demo accounts of the team keep signing in. It
     * is run here on a schema of its own: up to V5, an unverified account, then V6.
     */
    @Test
    void migrationV6_marksTheExistingAccountsAsVerified() throws Exception {
        execute("DROP SCHEMA IF EXISTS v6_check CASCADE");
        Flyway.configure().dataSource(dataSource).schemas("v6_check").locations("classpath:db/migration")
                .target("5").load().migrate();
        execute("INSERT INTO v6_check.users (username, email, password_hash, role, is_verified, bio) VALUES "
                + "('legacy', 'legacy@upc.edu.pe', '$2a$11$hash', 'Student', false, '')");

        Flyway.configure().dataSource(dataSource).schemas("v6_check").locations("classpath:db/migration")
                .target("6").load().migrate();

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT is_verified, verification_token_hash FROM v6_check.users WHERE username = 'legacy'")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getBoolean(1)).isTrue();
            assertThat(rows.getString(2)).isNull();
        } finally {
            execute("DROP SCHEMA v6_check CASCADE");
        }
    }
}
