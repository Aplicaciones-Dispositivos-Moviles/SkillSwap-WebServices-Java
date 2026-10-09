package com.innovify.skillswap.iam.interfaces.rest;

import com.innovify.skillswap.iam.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.iam.application.fakes.FakePasswordHasher;
import com.innovify.skillswap.iam.application.fakes.FakeTokenGenerator;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.application.fakes.MutableClock;
import com.innovify.skillswap.iam.application.internal.commandservices.EmailVerificationCommandServiceImpl;
import com.innovify.skillswap.iam.application.internal.commandservices.EmailVerificationIssuer;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.internal.commandservices.UserCommandServiceImpl;
import com.innovify.skillswap.iam.application.internal.queryservices.UserQueryServiceImpl;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.services.DefaultEmailDomainValidator;
import com.innovify.skillswap.shared.infrastructure.i18n.LatinAmericanSpanishLocaleResolver;
import com.innovify.skillswap.shared.interfaces.rest.RestExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Duration;
import java.util.List;

/**
 * Runs the IAM controllers in a standalone MockMvc over the real application services and in-memory fakes, so
 * no database is needed. The token filter and the URL rules are covered by SecurityIntegrationTest and
 * AuthenticationFlowIntegrationTest, which use a real PostgreSQL.
 */
abstract class IamRestTest {

    static final String SIGN_UP_URL = "/api/v1/authentication/sign-up";
    static final String SIGN_IN_URL = "/api/v1/authentication/sign-in";
    static final String PASSWORD = "password123";

    static final Duration TOKEN_TTL = Duration.ofHours(24);
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);

    protected final FakeUserRepository repository = new FakeUserRepository();
    protected final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    protected final MutableClock clock = new MutableClock();
    protected MockMvc mockMvc;

    @BeforeEach
    void setUpMockMvc() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        var issuer = new EmailVerificationIssuer(repository, events, TOKEN_TTL, RESEND_COOLDOWN, clock);
        var commands = new UserCommandServiceImpl(repository, new FakePasswordHasher(),
                new DefaultEmailDomainValidator(), new FakeTokenGenerator(), events, issuer, messages);
        var verification = new EmailVerificationCommandServiceImpl(repository, issuer, messages);
        var queries = new UserQueryServiceImpl(repository);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthenticationController(commands, verification, messages),
                        new UsersController(queries, commands, messages))
                .setControllerAdvice(new RestExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setLocaleResolver(new LatinAmericanSpanishLocaleResolver())
                .build();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /** Stores a user (the password is "password123" for the fake hasher) and returns it with its id. */
    protected User saveUser(String username, String email, UserRole role) {
        User user = TestData.newUser(username, email, role);
        return repository.save(user);
    }

    /** The verification emails requested so far for the address. */
    protected List<EmailVerificationRequested> verificationRequestsFor(String email) {
        return events.published().stream()
                .filter(EmailVerificationRequested.class::isInstance)
                .map(EmailVerificationRequested.class::cast)
                .filter(event -> event.email().equalsIgnoreCase(email))
                .toList();
    }

    /** The token of the last verification email requested for the address. */
    protected String lastVerificationTokenFor(String email) {
        List<EmailVerificationRequested> requests = verificationRequestsFor(email);
        if (requests.isEmpty()) {
            throw new AssertionError("No verification email was requested for " + email);
        }
        return requests.get(requests.size() - 1).token();
    }

    /** Makes the user the authenticated principal of the following requests, as the JWT filter does. */
    protected void authenticateAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                user, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    protected static String json(String... keysAndValues) {
        StringBuilder body = new StringBuilder("{");
        for (int i = 0; i < keysAndValues.length; i += 2) {
            if (i > 0) {
                body.append(',');
            }
            body.append('"').append(keysAndValues[i]).append("\":\"")
                    .append(keysAndValues[i + 1].replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return body.append('}').toString();
    }
}
