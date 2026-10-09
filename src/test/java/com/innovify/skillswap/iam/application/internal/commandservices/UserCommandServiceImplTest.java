package com.innovify.skillswap.iam.application.internal.commandservices;

import com.innovify.skillswap.iam.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.iam.application.fakes.FakePasswordHasher;
import com.innovify.skillswap.iam.application.fakes.FakeTokenGenerator;
import com.innovify.skillswap.iam.application.fakes.FakeSkillCatalog;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.application.fakes.MutableClock;
import com.innovify.skillswap.iam.application.internal.outboundservices.AuthenticatedUser;
import com.innovify.skillswap.iam.domain.model.IamError;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.RegisterDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.RemoveDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateInterestProfileCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import com.innovify.skillswap.iam.domain.model.events.UserRegistered;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.services.DefaultEmailDomainValidator;
import com.innovify.skillswap.shared.application.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class UserCommandServiceImplTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final MutableClock clock = new MutableClock();
    private UserCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        var issuer = new EmailVerificationIssuer(repository, events, Duration.ofHours(24), Duration.ofMinutes(2),
                clock);
        service = new UserCommandServiceImpl(repository, new FakePasswordHasher(),
                new DefaultEmailDomainValidator(), new FakeTokenGenerator(), events, issuer, new FakeSkillCatalog(),
                messages);
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

        assertThat(events.published()).hasSize(2);
        UserRegistered published = (UserRegistered) events.published().get(0);
        assertThat(published.userId()).isEqualTo(result.value().getId());
        assertThat(published.userId()).isPositive();
        assertThat(published.role()).isEqualTo(UserRole.STUDENT);
    }

    @Test
    void signUp_withValidData_storesAVerificationTokenHashAndRequestsTheEmail() {
        Result<User> result = service.handle(signUp("Ana", "Ana@UPC.edu.pe", "password123"));

        User user = result.value();
        EmailVerificationRequested requested = (EmailVerificationRequested) events.published().get(1);
        assertThat(requested.userId()).isEqualTo(user.getId());
        assertThat(requested.username()).isEqualTo("ana");
        assertThat(requested.email()).isEqualTo("ana@upc.edu.pe");
        assertThat(requested.issuedAt()).isEqualTo(clock.instant());
        assertThat(requested.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
        // Only the hash is stored, never the token of the email.
        assertThat(user.getVerificationTokenHash()).isEqualTo(EmailVerificationIssuer.hash(requested.token()));
        assertThat(user.getVerificationTokenHash()).isNotEqualTo(requested.token());
        assertThat(requested.toString()).doesNotContain(requested.token());
        assertThat(user.isVerified()).isFalse();
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
        service.handle(signUp("Ana", "ana@upc.edu.pe", "password123")).value().verify();

        Result<AuthenticatedUser> result = service.handle(new SignInCommand("ANA", "password123"));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().user().getUsername().value()).isEqualTo("ana");
        assertThat(result.value().token()).isEqualTo("token-for-1");
    }

    @Test
    void signIn_ofAnUnverifiedAccount_returnsEmailNotVerified() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));

        assertFailure(service.handle(new SignInCommand("ana", "password123")), IamError.EMAIL_NOT_VERIFIED);
    }

    @Test
    void signIn_ofAnUnverifiedAccount_requestsANewEmailOnlyAfterTheCooldown() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));
        String firstHash = repository.users().get(0).getVerificationTokenHash();
        events.published().clear();

        service.handle(new SignInCommand("ana", "password123"));
        assertThat(events.published()).isEmpty();

        clock.advance(Duration.ofMinutes(2));
        service.handle(new SignInCommand("ana", "password123"));

        assertThat(events.published()).singleElement().isInstanceOf(EmailVerificationRequested.class);
        assertThat(repository.users().get(0).getVerificationTokenHash()).isNotEqualTo(firstHash);
    }

    @Test
    void signIn_ofAnUnverifiedAccount_stillAnswersEmailNotVerifiedWhenTheNewTokenCannotBeSaved() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));
        events.published().clear();
        clock.advance(Duration.ofMinutes(5));
        repository.failOnSave(new DataIntegrityViolationException("boom"));

        assertFailure(service.handle(new SignInCommand("ana", "password123")), IamError.EMAIL_NOT_VERIFIED);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void signIn_ofAnUnverifiedAccountWithAWrongPassword_returnsInvalidCredentialsAndSendsNothing() {
        service.handle(signUp("ana", "ana@upc.edu.pe", "password123"));
        events.published().clear();
        clock.advance(Duration.ofMinutes(5));

        assertFailure(service.handle(new SignInCommand("ana", "wrong")), IamError.INVALID_CREDENTIALS);
        assertThat(events.published()).isEmpty();
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

    // ---------- Interest profile (US04) ----------

    @Test
    void updateInterestProfile_storesTopicsAndDescriptionAndCalculatesTheSkillVector() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new UpdateInterestProfileCommand(userId,
                List.of("Backend con Java", "  Bases   de datos SQL "), "Me gusta el testing", userId));

        assertThat(result.isSuccess()).isTrue();
        User user = result.value();
        assertThat(user.getInterestTopics()).containsExactly("Backend con Java", "Bases de datos SQL");
        assertThat(user.getBio()).isEqualTo("Me gusta el testing");
        assertThat(user.getSkillVector()).containsExactly("java-language", "sql-databases", "software-testing");
    }

    @Test
    void updateInterestProfile_replacesThePreviousTopicsAndRecalculatesTheVector() {
        int userId = service.handle(signUp()).value().getId();
        service.handle(new UpdateInterestProfileCommand(userId, List.of("Java", "SQL"), null, userId));

        Result<User> result = service.handle(new UpdateInterestProfileCommand(userId, List.of("React"), null,
                userId));

        assertThat(result.value().getInterestTopics()).containsExactly("React");
        assertThat(result.value().getSkillVector()).containsExactly("react");
    }

    @Test
    void updateInterestProfile_withoutDescription_keepsTheBioAndCountsItInTheVector() {
        int userId = service.handle(signUp()).value().getId();
        service.handle(new UpdateUserBioCommand(userId, "Estudio Java", userId));

        Result<User> result = service.handle(new UpdateInterestProfileCommand(userId, List.of("React"), null,
                userId));

        assertThat(result.value().getBio()).isEqualTo("Estudio Java");
        assertThat(result.value().getSkillVector()).containsExactly("react", "java-language");
    }

    @Test
    void updateInterestProfile_keepsTopicsThatMatchNoSkill() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new UpdateInterestProfileCommand(userId, List.of("Ajedrez"), "",
                userId));

        assertThat(result.value().getInterestTopics()).containsExactly("Ajedrez");
        assertThat(result.value().getSkillVector()).isEmpty();
    }

    @Test
    void updateInterestProfile_ignoresRepeatedTopics() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new UpdateInterestProfileCommand(userId,
                List.of("Java", "JAVA", " java "), null, userId));

        assertThat(result.value().getInterestTopics()).containsExactly("Java");
    }

    @Test
    void updateUserBio_recalculatesTheSkillVector() {
        int userId = service.handle(signUp()).value().getId();
        service.handle(new UpdateInterestProfileCommand(userId, List.of("React"), null, userId));

        Result<User> result = service.handle(new UpdateUserBioCommand(userId, "Ahora aprendo SQL", userId));

        assertThat(result.value().getSkillVector()).containsExactly("react", "sql-databases");
    }

    @Test
    void updateInterestProfile_withoutTopics_returnsInterestTopicsRequired() {
        int userId = service.handle(signUp()).value().getId();

        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, List.of(), "x", userId)),
                IamError.INTEREST_TOPICS_REQUIRED);
        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, null, "x", userId)),
                IamError.INTEREST_TOPICS_REQUIRED);
    }

    @Test
    void updateInterestProfile_withMoreThanTenDistinctTopics_returnsTooManyInterestTopics() {
        int userId = service.handle(signUp()).value().getId();
        List<String> topics = java.util.stream.IntStream.rangeClosed(1, 11).mapToObj(i -> "Tema " + i).toList();

        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, topics, null, userId)),
                IamError.TOO_MANY_INTEREST_TOPICS);
    }

    @Test
    void updateInterestProfile_withABlankOrTooLongTopic_returnsInvalidInterestTopic() {
        int userId = service.handle(signUp()).value().getId();

        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, List.of("Java", " "), null, userId)),
                IamError.INVALID_INTEREST_TOPIC);
        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, Arrays.asList("Java", null), null,
                userId)), IamError.INVALID_INTEREST_TOPIC);
        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, List.of("x".repeat(61)), null,
                userId)), IamError.INVALID_INTEREST_TOPIC);
    }

    @Test
    void updateInterestProfile_withATooLongDescription_returnsBioTooLongAndChangesNothing() {
        int userId = service.handle(signUp()).value().getId();

        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, List.of("Java"), "a".repeat(1001),
                userId)), IamError.BIO_TOO_LONG);
        assertThat(repository.users().get(0).getInterestTopics()).isEmpty();
    }

    @Test
    void updateInterestProfile_ofAnotherUser_returnsNotProfileOwner() {
        int userId = service.handle(signUp()).value().getId();

        assertFailure(service.handle(new UpdateInterestProfileCommand(userId, List.of("Java"), null, userId + 1)),
                IamError.NOT_PROFILE_OWNER);
    }

    @Test
    void updateInterestProfile_ofAnUnknownUser_returnsUserNotFound() {
        assertFailure(service.handle(new UpdateInterestProfileCommand(99, List.of("Java"), null, 99)),
                IamError.USER_NOT_FOUND);
    }

    // ---------- Device token ----------

    @Test
    void registerDeviceToken_storesTheTokenOfTheUser() {
        int userId = service.handle(signUp()).value().getId();

        Result<User> result = service.handle(new RegisterDeviceTokenCommand(userId, " fcm-token-1 "));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getDeviceToken().value()).isEqualTo("fcm-token-1");
    }

    @Test
    void registerDeviceToken_replacesThePreviousTokenOfTheUser() {
        int userId = service.handle(signUp()).value().getId();
        service.handle(new RegisterDeviceTokenCommand(userId, "old-token"));

        service.handle(new RegisterDeviceTokenCommand(userId, "new-token"));

        assertThat(repository.users().get(0).getDeviceToken().value()).isEqualTo("new-token");
    }

    @Test
    void registerDeviceToken_removesTheSameTokenFromAnotherAccount() {
        int anaId = service.handle(signUp("ana", "ana@upc.edu.pe", "password123")).value().getId();
        int bobId = service.handle(signUp("bob", "bob@upc.edu.pe", "password123")).value().getId();
        service.handle(new RegisterDeviceTokenCommand(anaId, "shared-device"));

        service.handle(new RegisterDeviceTokenCommand(bobId, "shared-device"));

        assertThat(repository.findById(anaId).orElseThrow().getDeviceToken()).isNull();
        assertThat(repository.findById(bobId).orElseThrow().getDeviceToken().value()).isEqualTo("shared-device");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "with space"})
    void registerDeviceToken_withAnInvalidToken_returnsInvalidDeviceToken(String token) {
        int userId = service.handle(signUp()).value().getId();

        assertFailure(service.handle(new RegisterDeviceTokenCommand(userId, token)), IamError.INVALID_DEVICE_TOKEN);
    }

    @Test
    void registerDeviceToken_tooLong_returnsInvalidDeviceToken() {
        int userId = service.handle(signUp()).value().getId();

        assertFailure(service.handle(new RegisterDeviceTokenCommand(userId, "x".repeat(513))),
                IamError.INVALID_DEVICE_TOKEN);
    }

    @Test
    void registerDeviceToken_forAnUnknownUser_returnsUserNotFound() {
        assertFailure(service.handle(new RegisterDeviceTokenCommand(99, "token")), IamError.USER_NOT_FOUND);
    }

    @Test
    void removeDeviceToken_forgetsTheToken() {
        int userId = service.handle(signUp()).value().getId();
        service.handle(new RegisterDeviceTokenCommand(userId, "token"));

        Result<User> result = service.handle(new RemoveDeviceTokenCommand(userId));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getDeviceToken()).isNull();
    }

    @Test
    void removeDeviceToken_withoutAToken_succeedsWithoutSaving() {
        int userId = service.handle(signUp()).value().getId();
        int savesBefore = repository.saveCalls();

        assertThat(service.handle(new RemoveDeviceTokenCommand(userId)).isSuccess()).isTrue();
        assertThat(repository.saveCalls()).isEqualTo(savesBefore);
    }

    @Test
    void removeDeviceToken_forAnUnknownUser_returnsUserNotFound() {
        assertFailure(service.handle(new RemoveDeviceTokenCommand(99)), IamError.USER_NOT_FOUND);
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
