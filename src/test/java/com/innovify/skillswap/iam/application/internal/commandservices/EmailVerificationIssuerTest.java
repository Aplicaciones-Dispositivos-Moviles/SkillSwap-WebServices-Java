package com.innovify.skillswap.iam.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.iam.application.fakes.FakeUserRepository;
import com.innovify.skillswap.iam.application.fakes.MutableClock;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.events.EmailVerificationRequested;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EmailVerificationIssuerTest {

    private final FakeUserRepository repository = new FakeUserRepository();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private final MutableClock clock = new MutableClock();
    private final EmailVerificationIssuer issuer = new EmailVerificationIssuer(repository, events,
            Duration.ofHours(24), Duration.ofMinutes(2), clock);

    @Test
    void issue_createsLongRandomUrlSafeTokensAndStoresOnlyTheirHash() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            User user = TestData.newUser();
            EmailVerificationIssuer.IssuedToken issued = issuer.issue(user);

            assertThat(issued.token()).matches("[A-Za-z0-9_-]{43}");
            assertThat(user.getVerificationTokenHash()).isEqualTo(EmailVerificationIssuer.hash(issued.token()));
            assertThat(issued.expiresAt()).isEqualTo(clock.instant().plus(Duration.ofHours(24)));
            assertThat(issued.toString()).doesNotContain(issued.token());
            tokens.add(issued.token());
        }
        assertThat(tokens).hasSize(50);
    }

    @Test
    void hash_isTheSha256InLowercaseHex() {
        // echo -n abc | sha256sum
        assertThat(EmailVerificationIssuer.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void reissue_savesTheNewTokenAndRequestsTheEmail() {
        User user = repository.save(TestData.newUser());

        assertThat(issuer.reissue(user)).isTrue();

        EmailVerificationRequested requested = (EmailVerificationRequested) events.published().get(0);
        assertThat(requested.userId()).isEqualTo(user.getId());
        assertThat(EmailVerificationIssuer.hash(requested.token())).isEqualTo(user.getVerificationTokenHash());
        assertThat(repository.saveCalls()).isEqualTo(2);
    }

    @Test
    void reissue_withinTheCooldown_doesNothing() {
        User user = repository.save(TestData.newUser());
        issuer.reissue(user);
        clock.advance(Duration.ofSeconds(119));

        assertThat(issuer.reissue(user)).isFalse();
        assertThat(events.published()).hasSize(1);
    }

    @Test
    void reissue_ofAVerifiedAccount_doesNothing() {
        User user = repository.save(TestData.newUser().verify());

        assertThat(issuer.reissue(user)).isFalse();
        assertThat(events.published()).isEmpty();
    }

    @Test
    void reissue_whenTheTokenCannotBeSaved_throwsAndRequestsNoEmail() {
        User user = repository.save(TestData.newUser());
        repository.failOnSave(new IllegalStateException("database down"));

        assertThatThrownBy(() -> issuer.reissue(user)).isInstanceOf(IllegalStateException.class);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void constructor_rejectsANonPositiveLifetimeOrANegativeCooldown() {
        assertThatThrownBy(() -> new EmailVerificationIssuer(repository, events, Duration.ZERO, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailVerificationIssuer(repository, events, Duration.ofHours(1),
                Duration.ofSeconds(-1), clock)).isInstanceOf(IllegalArgumentException.class);
    }
}
