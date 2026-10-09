package com.innovify.skillswap.iam.domain.model.aggregates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.iam.TestData;
import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

class UserTest {

    @Test
    void newUser_startsUnverifiedWithEmptyBioAndNoDeviceToken() {
        User user = TestData.newUser();

        assertThat(user.isVerified()).isFalse();
        assertThat(user.getBio()).isEmpty();
        assertThat(user.getDeviceToken()).isNull();
        assertThat(user.getId()).isNull();
    }

    @Test
    void verify_marksTheUserAsVerified() {
        User user = TestData.newUser();

        user.verify();

        assertThat(user.isVerified()).isTrue();
    }

    @Test
    void updateBio_trimsAndStoresTheText() {
        User user = TestData.newUser();

        user.updateBio("  Backend developer  ");

        assertThat(user.getBio()).isEqualTo("Backend developer");
    }

    @Test
    void updateBio_withExactlyMaxLength_isAccepted() {
        User user = TestData.newUser();

        user.updateBio("a".repeat(User.MAX_BIO_LENGTH));

        assertThat(user.getBio()).hasSize(User.MAX_BIO_LENGTH);
    }

    @Test
    void updateBio_exceedingMaxLength_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.updateBio("a".repeat(User.MAX_BIO_LENGTH + 1)))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void updateBio_withNull_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.updateBio(null)).isInstanceOf(DomainException.class);
    }

    @Test
    void registerDeviceToken_storesTheToken() {
        User user = TestData.newUser();

        user.registerDeviceToken(" device-123 ");

        assertThat(user.getDeviceToken()).isEqualTo(new DeviceToken("device-123"));
    }

    @Test
    void registerDeviceToken_withBlankToken_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.registerDeviceToken("  ")).isInstanceOf(DomainException.class);
    }

    @Test
    void registerDeviceToken_withWhitespaceInsideOrTooLong_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.registerDeviceToken("abc def")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.registerDeviceToken("x".repeat(DeviceToken.MAX_LENGTH + 1)))
                .isInstanceOf(DomainException.class);
        user.registerDeviceToken("x".repeat(DeviceToken.MAX_LENGTH));
        assertThat(user.hasDeviceToken()).isTrue();
    }

    @Test
    void removeDeviceToken_forgetsIt() {
        User user = TestData.newUser().registerDeviceToken("device-123");

        user.removeDeviceToken();

        assertThat(user.getDeviceToken()).isNull();
        assertThat(user.hasDeviceToken()).isFalse();
    }

    @Test
    void deviceToken_neverPrintsTheWholeToken() {
        DeviceToken token = new DeviceToken("fcm-registration-token-123456");

        assertThat(token.toString()).isEqualTo("DeviceToken[fcm-regi...]");
        assertThat(new DeviceToken("short").abbreviated()).isEqualTo("***");
    }

    // ---------- Interest profile ----------

    @Test
    void newUser_hasNoInterestsAndAnEmptySkillVector() {
        User user = TestData.newUser();

        assertThat(user.getInterestTopics()).isEmpty();
        assertThat(user.getSkillVector()).isEmpty();
    }

    @Test
    void replaceInterestTopics_normalizesSpacesAndRemovesRepeatedTopics() {
        User user = TestData.newUser();

        user.replaceInterestTopics(List.of("  Desarrollo   web ", "desarrollo web", "Java"));

        assertThat(user.getInterestTopics()).containsExactly("Desarrollo web", "Java");
    }

    @Test
    void replaceInterestTopics_withInvalidTopics_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.replaceInterestTopics(List.of())).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.replaceInterestTopics(List.of(" "))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.replaceInterestTopics(List.of("x".repeat(User.MAX_INTEREST_TOPIC_LENGTH + 1))))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.replaceInterestTopics(
                java.util.stream.IntStream.rangeClosed(1, User.MAX_INTEREST_TOPICS + 1).mapToObj(i -> "t" + i)
                        .toList())).isInstanceOf(DomainException.class);
    }

    @Test
    void updateSkillVector_keepsEachTagOnceAndDropsBlanks() {
        User user = TestData.newUser();

        user.updateSkillVector(java.util.Arrays.asList("java-language", " ", null, "java-language", "react"));

        assertThat(user.getSkillVector()).containsExactly("java-language", "react");
    }

    // ---------- Email verification ----------

    private static final String HASH = "a".repeat(64);
    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    @Test
    void issueVerificationToken_storesTheHashTheExpirationAndWhenItWasSent() {
        User user = TestData.newUser();

        user.issueVerificationToken(HASH, NOW.plus(Duration.ofHours(24)), NOW);

        assertThat(user.getVerificationTokenHash()).isEqualTo(HASH);
        assertThat(user.getVerificationTokenExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(user.getVerificationEmailSentAt()).isEqualTo(NOW);
        assertThat(user.isVerificationTokenExpired(NOW)).isFalse();
        assertThat(user.isVerificationTokenExpired(NOW.plus(Duration.ofHours(24)))).isTrue();
    }

    @Test
    void issueVerificationToken_onAVerifiedAccount_throwsDomainException() {
        User user = TestData.newUser().verify();

        assertThatThrownBy(() -> user.issueVerificationToken(HASH, NOW.plusSeconds(60), NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void issueVerificationToken_withSomethingThatIsNotASha256Hex_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.issueVerificationToken("plain-token", NOW.plusSeconds(60), NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.issueVerificationToken("A".repeat(64), NOW.plusSeconds(60), NOW))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.issueVerificationToken("z".repeat(64), NOW.plusSeconds(60), NOW))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void issueVerificationToken_thatExpiresBeforeItIsIssued_throwsDomainException() {
        User user = TestData.newUser();

        assertThatThrownBy(() -> user.issueVerificationToken(HASH, NOW, NOW)).isInstanceOf(DomainException.class);
    }

    @Test
    void verify_clearsThePendingTokenSoItCannotBeUsedAgain() {
        User user = TestData.newUser();
        user.issueVerificationToken(HASH, NOW.plus(Duration.ofHours(24)), NOW);

        user.verify();

        assertThat(user.getVerificationTokenHash()).isNull();
        assertThat(user.isVerificationTokenExpired(NOW)).isTrue();
    }

    @Test
    void canReceiveVerificationEmail_respectsTheCooldownAndNeverForAVerifiedAccount() {
        User user = TestData.newUser();
        assertThat(user.canReceiveVerificationEmail(NOW, Duration.ofMinutes(2))).isTrue();

        user.issueVerificationToken(HASH, NOW.plus(Duration.ofHours(24)), NOW);
        assertThat(user.canReceiveVerificationEmail(NOW.plusSeconds(119), Duration.ofMinutes(2))).isFalse();
        assertThat(user.canReceiveVerificationEmail(NOW.plusSeconds(120), Duration.ofMinutes(2))).isTrue();

        user.verify();
        assertThat(user.canReceiveVerificationEmail(NOW.plus(Duration.ofDays(1)), Duration.ofMinutes(2))).isFalse();
    }

    @Test
    void updateFullName_normalizesTheSpacesAndRejectsInvalidNames() {
        var user = TestData.newUser();

        assertThat(user.updateFullName("  Ana \t María  ").getFullName()).isEqualTo("Ana María");
        assertThat(user.updateFullName(null).getFullName()).isNull();
        assertThatThrownBy(() -> user.updateFullName("x".repeat(151))).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> user.updateFullName("Ana\u0007")).isInstanceOf(DomainException.class);
    }
}
