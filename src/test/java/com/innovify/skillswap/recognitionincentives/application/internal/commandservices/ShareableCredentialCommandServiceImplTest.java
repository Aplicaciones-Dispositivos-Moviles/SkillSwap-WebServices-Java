package com.innovify.skillswap.recognitionincentives.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.recognitionincentives.application.commandservices.IssuedCredential;
import com.innovify.skillswap.recognitionincentives.application.fakes.CredentialFacades;
import com.innovify.skillswap.recognitionincentives.application.fakes.FakeShareableCredentialRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.FakeWalletRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.TestMessages;
import com.innovify.skillswap.recognitionincentives.application.internal.CredentialViews;
import com.innovify.skillswap.recognitionincentives.domain.model.RecognitionIncentivesError;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.commands.CreateShareableCredentialCommand;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.services.SecureRandomCredentialTokenGenerator;
import com.innovify.skillswap.shared.application.Result;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

class ShareableCredentialCommandServiceImplTest {

    private static final int ANA = 5;
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final FakeShareableCredentialRepository credentials = new FakeShareableCredentialRepository();
    private final FakeWalletRepository wallets = new FakeWalletRepository();
    private final CredentialFacades.LearningPaths learningPaths = new CredentialFacades.LearningPaths();
    private ShareableCredentialCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        var views = new CredentialViews(wallets, new CredentialFacades.SkillCatalog().named("java-basics", "Java Basics")
                .facade(), new CredentialFacades.Iam().facade(), TestMessages.source(), "https://skillswap.test/");
        service = new ShareableCredentialCommandServiceImpl(credentials, learningPaths.facade(),
                new SecureRandomCredentialTokenGenerator(), views, Clock.fixed(NOW, ZoneOffset.UTC),
                TestMessages.source());
        wallets.save(new Wallet(ANA).credit(new Credits(120)));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private Result<IssuedCredential> create(int userId, String skillTag) {
        return service.handle(new CreateShareableCredentialCommand(userId, skillTag));
    }

    @Test
    void create_ofACompletedSkill_savesItAndReturnsTheLinkAndTheText() {
        learningPaths.completed(ANA, "java-basics");

        var result = create(ANA, " java-basics ");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().created()).isTrue();
        ShareableCredential saved = credentials.items().get(0);
        assertThat(saved.getOwnerId()).isEqualTo(ANA);
        assertThat(saved.getSkillTag()).isEqualTo("java-basics");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        var view = result.value().credential();
        assertThat(view.token()).isEqualTo(saved.getToken());
        assertThat(view.skillName()).isEqualTo("Java Basics");
        assertThat(view.url()).isEqualTo("https://skillswap.test/api/v1/public/credentials/" + saved.getToken());
        assertThat(view.shareText()).isEqualTo("I verified Java Basics on SkillSwap and I have 120 SkillCredits. Check it: "
                + view.url());
        assertThat(view.createdAt()).isEqualTo(NOW);
    }

    @Test
    void create_theShareTextFollowsTheLanguage() {
        learningPaths.completed(ANA, "java-basics");
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es-419"));

        var result = create(ANA, "java-basics");

        assertThat(result.value().credential().shareText()).startsWith("Verifiqué Java Basics en SkillSwap");
    }

    @Test
    void create_withoutWallet_showsZeroCredits() {
        learningPaths.completed(9, "java-basics");

        var result = create(9, "java-basics");

        assertThat(result.value().credential().shareText()).contains("have 0 SkillCredits");
    }

    @Test
    void create_aSkillWithoutNameInTheCatalog_usesTheTagAsName() {
        learningPaths.completed(ANA, "rust-basics");

        assertThat(create(ANA, "rust-basics").value().credential().skillName()).isEqualTo("rust-basics");
    }

    @Test
    void create_askingAgain_returnsTheSameLinkWithoutCreatingAnotherOne() {
        learningPaths.completed(ANA, "java-basics");
        String token = create(ANA, "java-basics").value().credential().token();

        var again = create(ANA, "java-basics");

        assertThat(again.isSuccess()).isTrue();
        assertThat(again.value().created()).isFalse();
        assertThat(again.value().credential().token()).isEqualTo(token);
        assertThat(credentials.items()).hasSize(1);
        assertThat(credentials.saveCalls()).isEqualTo(1);
    }

    @Test
    void create_twoSkillsOrTwoStudents_getDifferentTokens() {
        learningPaths.completed(ANA, "java-basics").completed(ANA, "sql-basics").completed(6, "java-basics");

        String first = create(ANA, "java-basics").value().credential().token();
        String second = create(ANA, "sql-basics").value().credential().token();
        String third = create(6, "java-basics").value().credential().token();

        assertThat(java.util.Set.of(first, second, third)).hasSize(3);
    }

    @Test
    void create_aSkillThatWasNotCompleted_isRejected() {
        var result = create(ANA, "java-basics");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(RecognitionIncentivesError.SKILL_NOT_VERIFIED);
        assertThat(result.message()).isEqualTo("You have not verified this skill yet, so you cannot share it.");
        assertThat(credentials.items()).isEmpty();
    }

    @Test
    void create_theSkillOfAnotherStudent_isRejected() {
        learningPaths.completed(6, "java-basics");

        assertThat(create(ANA, "java-basics").error()).isEqualTo(RecognitionIncentivesError.SKILL_NOT_VERIFIED);
    }

    @Test
    void create_withoutSkillOrWithAVeryLongOne_isRejectedWithoutAskingTheOtherContext() {
        assertThat(create(ANA, null).error()).isEqualTo(RecognitionIncentivesError.INVALID_SKILL_TAG);
        assertThat(create(ANA, "   ").error()).isEqualTo(RecognitionIncentivesError.INVALID_SKILL_TAG);
        assertThat(create(ANA, "a".repeat(101)).error()).isEqualTo(RecognitionIncentivesError.INVALID_SKILL_TAG);
        assertThat(learningPaths.calls()).isZero();
    }

    @Test
    void create_whenAnotherRequestWinsTheRace_returnsItsLink() {
        learningPaths.completed(ANA, "java-basics");
        credentials.beforeSave(() -> credentials.items().add(
                new ShareableCredential(ANA, "java-basics", "A".repeat(43), NOW)));

        var result = create(ANA, "java-basics");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().created()).isFalse();
        assertThat(result.value().credential().token()).isEqualTo("A".repeat(43));
    }

    @Test
    void create_whenTheDatabaseRefusesForAnotherReason_returnsDatabaseError() {
        learningPaths.completed(ANA, "java-basics");
        credentials.failOnSave(new DataIntegrityViolationException("x"));

        assertThat(create(ANA, "java-basics").error()).isEqualTo(RecognitionIncentivesError.DATABASE_ERROR);
    }

    @Test
    void create_withAFailureOfTheDatabase_returnsDatabaseError() {
        learningPaths.completed(ANA, "java-basics");
        credentials.failOnSave(new DataAccessResourceFailureException("down"));

        assertThat(create(ANA, "java-basics").error()).isEqualTo(RecognitionIncentivesError.DATABASE_ERROR);
    }

    @Test
    void create_withAnUnexpectedFailure_returnsInternalServerError() {
        learningPaths.completed(ANA, "java-basics");
        credentials.failOnSave(new IllegalStateException("boom"));

        assertThat(create(ANA, "java-basics").error()).isEqualTo(RecognitionIncentivesError.INTERNAL_SERVER_ERROR);
    }
}
