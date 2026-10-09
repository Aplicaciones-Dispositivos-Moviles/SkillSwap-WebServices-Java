package com.innovify.skillswap.iam.domain.model.aggregates;

import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.infrastructure.persistence.jpa.converters.StringListJsonConverter;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * User aggregate root. Centralizes the account information of a registered SkillSwap user. Every account is a
 * Student; a Student can also become a Verifier (see the Assessment &amp; Peer Review bounded context).
 *
 * <p>Value objects are mapped to their column by the auto-applied attribute converters of the
 * infrastructure layer.
 */
@Entity
@Table(name = "users")
public class User {

    public static final int MAX_BIO_LENGTH = 1000;
    public static final int MAX_INTEREST_TOPICS = 10;
    public static final int MAX_INTEREST_TOPIC_LENGTH = 60;
    public static final int MAX_FULL_NAME_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "username", nullable = false, length = 100)
    private Username username;

    @Column(name = "email", nullable = false, length = 255)
    private Email email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private PasswordHash passwordHash;

    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "bio", nullable = false, length = MAX_BIO_LENGTH)
    private String bio = "";

    @Column(name = "device_token", length = 512)
    private DeviceToken deviceToken;

    /** The topics the student is interested in, as they wrote them (US04). */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "interest_topics", nullable = false, columnDefinition = "jsonb")
    private List<String> interestTopics = List.of();

    /**
     * The skill vector of the student: the skills of the internal catalog their interest topics and description
     * refer to, used to personalize the paths and the matching with verifiers. It is recalculated whenever the
     * topics or the description change.
     */
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "skill_vector", nullable = false, columnDefinition = "jsonb")
    private List<String> skillVector = List.of();

    // Email verification: only the SHA-256 of the token sent by email is stored, never the token itself.
    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "verification_email_sent_at")
    private Instant verificationEmailSentAt;

    /** The real name of the student, compared with the holder of their certificates; null when not given. */
    @Column(name = "full_name", length = MAX_FULL_NAME_LENGTH)
    private String fullName;

    /** Required by JPA. */
    protected User() {
    }

    public User(Username username, Email email, PasswordHash passwordHash, UserRole role) {
        this.username = Objects.requireNonNull(username, "username");
        this.email = Objects.requireNonNull(email, "email");
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.role = Objects.requireNonNull(role, "role");
    }

    /**
     * Mark the account as verified once the institutional validation is confirmed. The pending verification
     * token, if any, can no longer be used.
     */
    public User verify() {
        this.verified = true;
        this.verificationTokenHash = null;
        this.verificationTokenExpiresAt = null;
        return this;
    }

    /**
     * Stores a new email verification token, replacing the previous one (whose link stops working).
     *
     * @param tokenHash the SHA-256 of the token, in hexadecimal
     * @param expiresAt when the token stops being valid
     * @param issuedAt  when the verification email is requested
     * @throws DomainException when the account is already verified or the values are not valid
     */
    public User issueVerificationToken(String tokenHash, Instant expiresAt, Instant issuedAt) {
        if (verified) {
            throw new DomainException("The email of the account is already verified.");
        }
        if (!isSha256Hex(tokenHash)) {
            throw new DomainException("The verification token hash must be a SHA-256 in hexadecimal.");
        }
        if (expiresAt == null || issuedAt == null || !expiresAt.isAfter(issuedAt)) {
            throw new DomainException("The verification token must expire after it is issued.");
        }
        this.verificationTokenHash = tokenHash;
        this.verificationTokenExpiresAt = expiresAt;
        this.verificationEmailSentAt = issuedAt;
        return this;
    }

    /**
     * Whether a new verification email may be sent now: the account is not verified and the last one was sent
     * at least {@code cooldown} ago, so the inbox of the student cannot be flooded.
     */
    public boolean canReceiveVerificationEmail(Instant now, Duration cooldown) {
        return !verified
                && (verificationEmailSentAt == null || !now.isBefore(verificationEmailSentAt.plus(cooldown)));
    }

    /** Whether the pending verification token can no longer be used (or there is none). */
    public boolean isVerificationTokenExpired(Instant now) {
        return verificationTokenExpiresAt == null || !now.isBefore(verificationTokenExpiresAt);
    }

    private static boolean isSha256Hex(String value) {
        if (value == null || value.length() != 64) {
            return false;
        }
        try {
            HexFormat.of().parseHex(value);
            return value.chars().noneMatch(Character::isUpperCase);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Update the free-text profile description.
     *
     * @throws DomainException when the bio is null or exceeds {@link #MAX_BIO_LENGTH} characters
     */
    public User updateBio(String bio) {
        if (bio == null) {
            throw new DomainException("The bio cannot be null.");
        }
        String stripped = bio.strip();
        if (stripped.length() > MAX_BIO_LENGTH) {
            throw new DomainException("The bio cannot exceed %d characters.".formatted(MAX_BIO_LENGTH));
        }
        this.bio = stripped;
        return this;
    }

    /**
     * Replace the interest topics of the profile. Blank spaces are collapsed and repeated topics (ignoring case) are
     * kept once, in the order given.
     *
     * @throws DomainException when there is no topic, more than {@link #MAX_INTEREST_TOPICS}, or a topic is blank
     *                         or longer than {@link #MAX_INTEREST_TOPIC_LENGTH} characters
     */
    public User replaceInterestTopics(List<String> topics) {
        this.interestTopics = normalizeInterestTopics(topics);
        return this;
    }

    /**
     * The normalized topics, or the reason they are not valid.
     *
     * @throws DomainException when they are not valid
     */
    public static List<String> normalizeInterestTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) {
            throw new DomainException("At least one interest topic is required.");
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String topic : topics) {
            if (topic == null || topic.isBlank()) {
                throw new DomainException("An interest topic cannot be empty.");
            }
            String normalized = topic.strip().replaceAll("\\s+", " ");
            if (normalized.length() > MAX_INTEREST_TOPIC_LENGTH) {
                throw new DomainException("An interest topic cannot exceed %d characters."
                        .formatted(MAX_INTEREST_TOPIC_LENGTH));
            }
            unique.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
        }
        if (unique.size() > MAX_INTEREST_TOPICS) {
            throw new DomainException("A profile cannot have more than %d interest topics."
                    .formatted(MAX_INTEREST_TOPICS));
        }
        return List.copyOf(unique.values());
    }

    /**
     * Store the skill vector recalculated from the interest topics and the description: catalog skill tags,
     * without blanks or duplicates.
     */
    public User updateSkillVector(List<String> skillTags) {
        List<String> tags = new ArrayList<>();
        for (String tag : skillTags == null ? List.<String>of() : skillTags) {
            if (tag != null && !tag.isBlank() && !tags.contains(tag.strip())) {
                tags.add(tag.strip());
            }
        }
        this.skillVector = List.copyOf(tags);
        return this;
    }

    /**
     * Sets the real name of the student, the one their certificates must be issued to. A blank name clears it.
     *
     * @throws DomainException when the name exceeds {@link #MAX_FULL_NAME_LENGTH} characters or contains control
     *                         characters
     */
    public User updateFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            this.fullName = null;
            return this;
        }
        String normalized = fullName.strip().replaceAll("\\s+", " ");
        if (!isValidFullName(normalized)) {
            throw new DomainException("The full name must have up to %d characters and no control characters."
                    .formatted(MAX_FULL_NAME_LENGTH));
        }
        this.fullName = normalized;
        return this;
    }

    /** Whether the name (already stripped) can be stored: a blank name is valid, it means none. */
    public static boolean isValidFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return true;
        }
        // Runs of whitespace (tabs and line breaks included) become one space, as they are stored.
        String normalized = fullName.strip().replaceAll("\\s+", " ");
        return normalized.length() <= MAX_FULL_NAME_LENGTH
                && normalized.codePoints().noneMatch(Character::isISOControl);
    }

    /** Associate or update the device token of the mobile device the user signed in from. */
    public User registerDeviceToken(String token) {
        this.deviceToken = new DeviceToken(token);
        return this;
    }

    /**
     * Forget the device token: the student denied (or revoked) the notification permission, signed out, or the
     * push provider reported the token as no longer valid. No push notification is sent until a new one is
     * registered.
     */
    public User removeDeviceToken() {
        this.deviceToken = null;
        return this;
    }

    public boolean hasDeviceToken() {
        return deviceToken != null;
    }

    /** Null until the account is persisted. */
    public Integer getId() {
        return id;
    }

    public Username getUsername() {
        return username;
    }

    public Email getEmail() {
        return email;
    }

    public PasswordHash getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isVerified() {
        return verified;
    }

    public String getBio() {
        return bio;
    }

    /** The interest topics, empty until the student registers them. */
    public List<String> getInterestTopics() {
        return interestTopics == null ? List.of() : List.copyOf(interestTopics);
    }

    /** The skill tags of the catalog the profile refers to; empty when nothing matched. */
    public List<String> getSkillVector() {
        return skillVector == null ? List.of() : List.copyOf(skillVector);
    }

    /** Null when there is no pending verification token. */
    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    /** Null when there is no pending verification token. */
    public Instant getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    /** Null until the first verification email is requested. */
    public Instant getVerificationEmailSentAt() {
        return verificationEmailSentAt;
    }

    /** The registered real name; null when the student did not give one. */
    public String getFullName() {
        return fullName;
    }

    /** Null until the mobile client registers one. */
    public DeviceToken getDeviceToken() {
        return deviceToken;
    }
}
