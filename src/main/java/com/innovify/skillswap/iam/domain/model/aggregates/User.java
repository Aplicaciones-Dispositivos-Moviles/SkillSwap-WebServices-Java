package com.innovify.skillswap.iam.domain.model.aggregates;

import com.innovify.skillswap.iam.domain.model.valueobjects.*;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.*;

import java.util.Objects;

/**
 * User aggregate root. Centralizes the account information of a registered SkillSwap user. Student accounts
 * can also become Verifiers (see the Assessment &amp; Peer Review bounded context); Coordinator is a
 * separate role.
 *
 * <p>Value objects are mapped to their column by the auto-applied attribute converters of the
 * infrastructure layer.
 */
@Entity
@Table(name = "users")
public class User {

    public static final int MAX_BIO_LENGTH = 1000;

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

    /** Null until the mobile client registers one. */
    public DeviceToken getDeviceToken() {
        return deviceToken;
    }
}
