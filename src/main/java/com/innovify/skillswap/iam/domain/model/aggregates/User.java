package com.innovify.skillswap.iam.domain.model.aggregates;

import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.PasswordHash;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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

    /** Mark the account as verified once the institutional validation is confirmed. */
    public User verify() {
        this.verified = true;
        return this;
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

    /** The registered real name; null when the student did not give one. */
    public String getFullName() {
        return fullName;
    }

    /** Null until the mobile client registers one. */
    public DeviceToken getDeviceToken() {
        return deviceToken;
    }
}
