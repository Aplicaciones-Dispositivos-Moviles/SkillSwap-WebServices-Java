package com.innovify.skillswap.iam.application.internal.commandservices;

import com.innovify.skillswap.iam.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.iam.application.fakes.FakePasswordHasher;
import com.innovify.skillswap.iam.application.fakes.FakeTokenGenerator;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.application.internal.outboundservices.AuthenticatedUser;
import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.services.DefaultEmailDomainValidator;
import com.innovify.skillswap.shared.application.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UserCommandServiceImplTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private UserCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        service = new UserCommandServiceImpl(repository, new FakePasswordHasher(),
                new DefaultEmailDomainValidator(), new FakeTokenGenerator(), events, messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static SignUpCommand signUp(String username, String email, String password) {
        return new SignUpCommand(username, email, password, UserRole.STUDENT);
    }

    private static SignUpCommand signUp() {
        return signUp("Ana", "ana@upc.edu.pe", "password123");
    }

    private static void assertFailure(Result<?> result, IamError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
    }

    // ---------- Sign up ----------

    @Test
    void signUp_withAFullName_storesItNormalized() {
        Result<User> result = service.handle(new SignUpCommand("ana", "ana@upc.edu.pe", "password123",
                UserRole.STUDENT, "  Ana   María Pérez "));

        assertThat(result.value().getFullName()).isEqualTo("Ana María Pérez");
    }

    @Test
    void signUp_withAFullNameTooLong_isRejected() {
        Result<User> result = service.handle(new SignUpCommand("ana", "ana@upc.edu.pe", "password123",
                UserRole.STUDENT, "a".repeat(151)));

        assertFailure(result, IamError.INVALID_FULL_NAME);
        assertThat(repository.users()).isEmpty();
    }

    @Test
    void updateFullName_ofTheOwnProfile_storesItAndBlankClearsIt() {
        User user = service.handle(signUp()).value();

        Result<User> updated = service.handle(new com.innovify.skillswap.iam.domain.model.commands
                .UpdateUserFullNameCommand(user.getId(), "Ana Pérez", user.getId()));
        assertThat(updated.value().getFullName()).isEqualTo("Ana Pérez");

        Result<User> cleared = service.handle(new com.innovify.skillswap.iam.domain.model.commands
                .UpdateUserFullNameCommand(user.getId(), "  ", user.getId()));
        assertThat(cleared.value().getFullName()).isNull();
    }

    @Test
    void updateFullName_rejectsAnotherProfileAnUnknownUserAndAnInvalidName() {
        User user = service.handle(signUp()).value();

        assertFailure(service.handle(new com.innovify.skillswap.iam.domain.model.commands
                .UpdateUserFullNameCommand(user.getId(), "Ana", user.getId() + 1)), IamError.NOT_PROFILE_OWNER);
        assertFailure(service.handle(new com.innovify.skillswap.iam.domain.model.commands
                .UpdateUserFullNameCommand(99, "Ana", 99)), IamError.USER_NOT_FOUND);
        assertFailure(service.handle(new com.innovify.skillswap.iam.domain.model.commands
                .UpdateUserFullNameCommand(user.getId(), "Ana\u0000", user.getId())), IamError.INVALID_FULL_NAME);
    }

    @Test
    void signUp_withValidData_createsUserWithHashedPasswordAndNormalizedValues() {
        Result<User> result = service.handle(signUp("Ana", "Ana@UPC.edu.pe", "password123"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(repository.users()).hasSize(1);
        User user = repository.users().get(0);
        assertThat(user.getUsername().value()).isEqualTo("ana");
        assertThat(user.getEmail().value()).isEqualTo("ana@upc.edu.pe");
        assertThat(user.getPasswordHash().value()).isEqualTo("hashed:password123");
        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(user.isVerified()).isFalse();
        assertThat(repository.saveCalls()).isEqualTo(1);
    }

    @Test
    void signUp_withValidData_publishesTheUserRegisteredEvent() {
        Result<User> result = service.handle(signUp());

        assertThat(events.published()).hasSize(1);
        UserRegistered published = (UserRegistered) events.published().get(0);
        assertThat(published.userId()).isEqualTo(result.value().getId());
        assertThat(published.userId()).isPositive();
        assertThat(published.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void signUp_whenTheUsernameIsTaken_publishesNothing() {
        service.handle(signUp());
        events.published().clear();

        Result<User> result = service.handle(signUp("Ana", "other@upc.edu.pe", "password123"));

        assertFailure(result, IamError.USERNAME_ALREADY_TAKEN);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void signUp_withNonInstitutionalEmail_publishesNothing() {
        Result<User> result = service.handle(signUp("Ana", "ana@gmail.com", "password123"));

        assertThat(result.isFailure()).isTrue();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void signUp_whenPersistenceFails_publishesNothing() {
        repository.failOnSave(new DataIntegrityViolationException("failure"));

        Result<User> result = service.handle(signUp());

        assertFailure(result, IamError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@gmail.com", "ana@upc.edu", "ana@edu.pe", ""})
    void signUp_withNonInstitutionalEmail_fails(String email) {
        Result<User> result = service.handle(signUp("Ana", email, "password123"));

        assertFailure(result, IamError.INVALID_INSTITUTIONAL_EMAIL);
        assertThat(repository.users()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "with space"})
    void signUp_withInvalidUsername_fails(String username) {
        Result<User> result = service.handle(signUp(username, "ana@upc.edu.pe", "password123"));

        assertFailure(result, IamError.INVALID_USERNAME);
        assertThat(repository.users()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "short", "1234567"})
    void signUp_withShortPassword_fails(String password) {
        Result<User> result = service.handle(signUp("Ana", "ana@upc.edu.pe", password));

        assertFailure(result, IamError.WEAK_PASSWORD);
        assertThat(repository.users()).isEmpty();
    }

    @Test
    void signUp_withPasswordOver72Bytes_fails() {
        Result<User> result = service.handle(signUp("Ana", "ana@upc.edu.pe", "a".repeat(73)));

        assertFailure(result, IamError.WEAK_PASSWORD);
    }

    @Test
    void signUp_withMultiByteCharactersExceeding72Bytes_fails() {
        // 37 characters, 74 bytes in UTF-8
        Result<User> result = service.handle(signUp("Ana", "ana@upc.edu.pe", "ñ".repeat(37)));

        assertFailure(result, IamError.WEAK_PASSWORD);
    }

    @Test
    void signUp_withPasswordOfExactly72Bytes_succeeds() {
        Result<User> result = service.handle(signUp("Ana", "ana@upc.edu.pe", "a".repeat(72)));

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void signUp_withTakenUsername_fails() {
        service.handle(signUp("Ana", "ana@upc.edu.pe", "password123"));

        Result<User> result = service.handle(signUp("ANA", "other@upc.edu.pe", "password123"));

        assertFailure(result, IamError.USERNAME_ALREADY_TAKEN);
        assertThat(repository.users()).hasSize(1);
    }

    @Test
    void signUp_withTakenEmail_fails() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));

        Result<User> result = service.handle(signUp("other", "ANA@upc.edu.pe", "password123"));

        assertFailure(result, IamError.EMAIL_ALREADY_TAKEN);
        assertThat(repository.users()).hasSize(1);
    }

    @Test
    void signUp_whenSavingFailsWithADataAccessException_returnsDatabaseError() {
        repository.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(service.handle(signUp()), IamError.DATABASE_ERROR);
    }

    @Test
    void signUp_whenSavingFailsUnexpectedly_returnsInternalServerError() {
        repository.failOnSave(new IllegalStateException("boom"));

        assertFailure(service.handle(signUp()), IamError.INTERNAL_SERVER_ERROR);
    }

    // ---------- Sign in ----------

    @Test
    void signIn_withCorrectCredentials_returnsUserAndToken() {
        service.handle(signUp("Ana", "ana@upc.edu.pe", "password123"));

        Result<AuthenticatedUser> result = service.handle(new SignInCommand("ANA", "password123"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().user().getUsername().value()).isEqualTo("ana");
        assertThat(result.value().token()).isEqualTo("token-for-1");
    }

    @Test
    void signIn_withWrongPassword_returnsInvalidCredentials() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));

        assertFailure(service.handle(new SignInCommand("ana", "wrong-password")), IamError.INVALID_CREDENTIALS);
    }

    @Test
    void signIn_withUnknownUser_returnsSameErrorAsWrongPassword() {
        assertFailure(service.handle(new SignInCommand("nobody", "password123")), IamError.INVALID_CREDENTIALS);
    }

    @Test
    void signIn_withMalformedUsername_returnsInvalidCredentials() {
        assertFailure(service.handle(new SignInCommand("", "password123")), IamError.INVALID_CREDENTIALS);
    }

    @Test
    void signIn_withoutPassword_returnsInvalidCredentials() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));

        assertFailure(service.handle(new SignInCommand("ana", null)), IamError.INVALID_CREDENTIALS);
    }

    // ---------- Update bio ----------

    @Test
    void updateBio_byOwner_updatesAndSaves() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new UpdateUserBioCommand(userId, "  Hello world  ", userId));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getBio()).isEqualTo("Hello world");
        assertThat(repository.saveCalls()).isEqualTo(2);
    }

    @Test
    void updateBio_byAnotherUser_returnsNotProfileOwner() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new UpdateUserBioCommand(userId, "Hello", userId + 1));

        assertFailure(result, IamError.NOT_PROFILE_OWNER);
        assertThat(repository.users().get(0).getBio()).isEmpty();
    }

    @Test
    void updateBio_forUnknownUser_returnsUserNotFound() {
        assertFailure(service.handle(new UpdateUserBioCommand(99, "Hello", 99)), IamError.USER_NOT_FOUND);
    }

    @Test
    void updateBio_exceedingMaxLength_returnsBioTooLong() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new UpdateUserBioCommand(userId, "a".repeat(1001), userId));

        assertFailure(result, IamError.BIO_TOO_LONG);
    }

    @Test
    void updateBio_withoutText_clearsTheBio() {
        int userId = service.handle(signUp()).value().getId();
        service.handle(new UpdateUserBioCommand(userId, "Hello", userId));

        Result<User> result = service.handle(new UpdateUserBioCommand(userId, null, userId));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getBio()).isEmpty();
    }

    // ---------- Localized messages ----------

    @Test
    void failure_usesTheEnglishMessageByDefault() {
        Result<?> result = service.handle(new SignInCommand("nobody", "password123"));

        assertThat(result.message()).isEqualTo("Invalid username or password.");
    }

    @Test
    void failure_usesTheLatinAmericanSpanishMessageWhenTheLocaleIsEs419() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));

        Result<?> result = service.handle(new SignInCommand("nobody", "password123"));

        assertThat(result.message()).isEqualTo("Usuario o contraseña incorrectos.");
    }
}
