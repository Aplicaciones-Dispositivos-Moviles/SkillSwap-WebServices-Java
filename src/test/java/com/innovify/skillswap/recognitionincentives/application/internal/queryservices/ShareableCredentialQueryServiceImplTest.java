package com.innovify.skillswap.recognitionincentives.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.recognitionincentives.application.fakes.CredentialFacades;
import com.innovify.skillswap.recognitionincentives.application.fakes.FakeShareableCredentialRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.FakeWalletRepository;
import com.innovify.skillswap.recognitionincentives.application.fakes.TestMessages;
import com.innovify.skillswap.recognitionincentives.application.internal.CredentialViews;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.Wallet;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetPublicCredentialByTokenQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetShareableCredentialsByOwnerQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.services.CredentialTokenGenerator;
import com.innovify.skillswap.recognitionincentives.domain.services.SecureRandomCredentialTokenGenerator;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

class ShareableCredentialQueryServiceImplTest {

    private static final int ANA = 5;

    private final CredentialTokenGenerator tokens = new SecureRandomCredentialTokenGenerator();
    private final FakeShareableCredentialRepository credentials = new FakeShareableCredentialRepository();
    private final FakeWalletRepository wallets = new FakeWalletRepository();
    private ShareableCredentialQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        var views = new CredentialViews(wallets,
                new CredentialFacades.SkillCatalog().named("java-basics", "Java Basics")
                        .named("sql-basics", "SQL Basics").facade(),
                new CredentialFacades.Iam().named(ANA, "Ana Torres").facade(), TestMessages.source(),
                "https://skillswap.test");
        service = new ShareableCredentialQueryServiceImpl(credentials, views);
        wallets.save(new Wallet(ANA).credit(new Credits(75)));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private ShareableCredential save(int ownerId, String skillTag, String createdAt) {
        return credentials.save(new ShareableCredential(ownerId, skillTag, tokens.generate(), Instant.parse(createdAt)));
    }

    @Test
    void publicView_showsTheNameTheSkillAndTheCreditsOnly() {
        ShareableCredential credential = save(ANA, "java-basics", "2026-10-22T10:00:00Z");

        var view = service.handle(new GetPublicCredentialByTokenQuery(credential.getToken())).orElseThrow();

        assertThat(view.fullName()).isEqualTo("Ana Torres");
        assertThat(view.skillTag()).isEqualTo("java-basics");
        assertThat(view.skillName()).isEqualTo("Java Basics");
        assertThat(view.skillCredits()).isEqualTo(75);
        assertThat(view.issuedAt()).isEqualTo(Instant.parse("2026-10-22T10:00:00Z"));
    }

    @Test
    void publicView_withoutNameOrWallet_hasNullNameAndZeroCredits() {
        ShareableCredential credential = save(8, "java-basics", "2026-10-22T10:00:00Z");

        var view = service.handle(new GetPublicCredentialByTokenQuery(credential.getToken())).orElseThrow();

        assertThat(view.fullName()).isNull();
        assertThat(view.skillCredits()).isZero();
    }

    @Test
    void publicView_ofAnUnknownOrBlankToken_isEmpty() {
        save(ANA, "java-basics", "2026-10-22T10:00:00Z");

        assertThat(service.handle(new GetPublicCredentialByTokenQuery("x".repeat(43)))).isEmpty();
        assertThat(service.handle(new GetPublicCredentialByTokenQuery("  "))).isEmpty();
        assertThat(service.handle(new GetPublicCredentialByTokenQuery(null))).isEmpty();
    }

    @Test
    void list_returnsTheCredentialsOfTheStudentNewestFirstWithLinkAndText() {
        save(ANA, "java-basics", "2026-10-20T10:00:00Z");
        ShareableCredential newest = save(ANA, "sql-basics", "2026-10-22T10:00:00Z");
        save(8, "java-basics", "2026-10-23T10:00:00Z");

        var list = service.handle(new GetShareableCredentialsByOwnerQuery(ANA));

        assertThat(list).extracting("skillName").containsExactly("SQL Basics", "Java Basics");
        assertThat(list.get(0).url()).isEqualTo("https://skillswap.test/api/v1/public/credentials/" + newest.getToken());
        assertThat(list.get(0).shareText()).contains("SQL Basics").contains("75 SkillCredits").contains(list.get(0).url());
    }

    @Test
    void list_ofAStudentWithoutCredentials_isEmpty() {
        assertThat(service.handle(new GetShareableCredentialsByOwnerQuery(ANA))).isEmpty();
    }
}
