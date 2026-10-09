package com.innovify.skillswap.iam.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.application.fakes.MutableClock;
import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.ResendVerificationEmailCommand;
import com.innovify.skillswap.iam.domain.model.commands.VerifyEmailCommand;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;

class EmailVerificationCommandServiceImplTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final MutableClock clock = new MutableClock();
    private final EmailVerificationIssuer issuer = new EmailVerificationIssuer(repository, events,
            Duration.ofHours(24), Duration.ofMinutes(2), clock);
    private EmailVerificationCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        LocaleContextHolder.setLocale(Locale.US);
        service = new EmailVerificationCommandServiceImpl(repository, issuer, messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** An unverified account with a pending token; returns the token of its email. */
    private String unverifiedUserWithToken(String username, String email) {
        User user = TestData.newUser(username, email, UserRole.STUDENT);
        EmailVerificationIssuer.IssuedToken issued = issuer.issue(user);
        repository.save(user);
        return issued.token();
    }

    @Test
    void verify_withAValidToken_verifiesAndSavesTheUser() {
        String token = unverifiedUserWithToken("ana", "ana@upc.edu.pe");

        Result<User> result = service.handle(new VerifyEmailCommand("  " + token + " "));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().isVerified()).isTrue();
        assertThat(result.value().getVerificationTokenHash()).isNull();
        assertThat(repository.saveCalls()).isEqualTo(2);
    }

    @Test
    void verify_onlyVerifiesTheOwnerOfTheToken() {
        unverifiedUserWithToken("ana", "ana@upc.edu.pe");
        String bobToken = unverifiedUserWithToken("bob", "bob@upc.edu.pe");

        service.handle(new VerifyEmailCommand(bobToken));

        assertThat(repository.users().get(0).isVerified()).isFalse();
        assertThat(repository.users().get(1).isVerified()).isTrue();
    }

    @Test
    void verify_withAUsedToken_returnsInvalidVerificationToken() {
        String token = unverifiedUserWithToken("ana", "ana@upc.edu.pe");
        service.handle(new VerifyEmailCommand(token));

        Result<User> again = service.handle(new VerifyEmailCommand(token));

        assertThat(again.error()).isEqualTo(IamError.INVALID_VERIFICATION_TOKEN);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "unknown-token"})
    void verify_withAMissingOrUnknownToken_returnsInvalidVerificationToken(String token) {
        unverifiedUserWithToken("ana", "ana@upc.edu.pe");

        assertThat(service.handle(new VerifyEmailCommand(token)).error())
                .isEqualTo(IamError.INVALID_VERIFICATION_TOKEN);
    }

    @Test
    void verify_withAnAbsurdlyLongToken_returnsInvalidVerificationTokenWithoutLookingItUp() {
        assertThat(service.handle(new VerifyEmailCommand("x".repeat(10_000))).error())
                .isEqualTo(IamError.INVALID_VERIFICATION_TOKEN);
    }

    @Test
    void verify_withAnExpiredToken_returnsVerificationTokenExpired() {
        String token = unverifiedUserWithToken("ana", "ana@upc.edu.pe");
        clock.advance(Duration.ofHours(24));

        Result<User> result = service.handle(new VerifyEmailCommand(token));

        assertThat(result.error()).isEqualTo(IamError.VERIFICATION_TOKEN_EXPIRED);
        assertThat(result.message()).isEqualTo("The verification link has expired. Sign in to receive a new one.");
        assertThat(repository.users().get(0).isVerified()).isFalse();
    }

    @Test
    void verify_whenSavingFails_returnsDatabaseError() {
        String token = unverifiedUserWithToken("ana", "ana@upc.edu.pe");
        repository.failOnSave(new DataIntegrityViolationException("down"));

        assertThat(service.handle(new VerifyEmailCommand(token)).error()).isEqualTo(IamError.DATABASE_ERROR);
    }

    @Test
    void resend_forAnUnverifiedAccountAfterTheCooldown_requestsANewEmail() {
        unverifiedUserWithToken("ana", "ana@upc.edu.pe");
        clock.advance(Duration.ofMinutes(2));

        Result<Void> result = service.handle(new ResendVerificationEmailCommand(" ANA@upc.edu.pe "));

        assertThat(result.isSuccess()).isTrue();
        assertThat(events.published()).singleElement().isInstanceOf(EmailVerificationRequested.class);
    }

    @Test
    void resend_withinTheCooldown_succeedsWithoutSendingAnything() {
        unverifiedUserWithToken("ana", "ana@upc.edu.pe");

        assertThat(service.handle(new ResendVerificationEmailCommand("ana@upc.edu.pe")).isSuccess()).isTrue();
        assertThat(events.published()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nobody@upc.edu.pe", "someone@gmail.com", "not-an-email"})
    void resend_forUnknownOrInvalidEmails_succeedsWithoutSendingAnything(String email) {
        assertThat(service.handle(new ResendVerificationEmailCommand(email)).isSuccess()).isTrue();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void resend_forAVerifiedAccount_succeedsWithoutSendingAnything() {
        repository.save(TestData.newUser().verify());
        clock.advance(Duration.ofHours(1));

        assertThat(service.handle(new ResendVerificationEmailCommand("ana@upc.edu.pe")).isSuccess()).isTrue();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void resend_whenTheNewTokenCannotBeSaved_stillSucceeds() {
        unverifiedUserWithToken("ana", "ana@upc.edu.pe");
        clock.advance(Duration.ofMinutes(5));
        repository.failOnSave(new DataIntegrityViolationException("down"));

        assertThat(service.handle(new ResendVerificationEmailCommand("ana@upc.edu.pe")).isSuccess()).isTrue();
        assertThat(events.published()).isEmpty();
    }
}
