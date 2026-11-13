package com.innovify.skillswap.recognitionincentives.domain.model.aggregates;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * The public link of a verified skill of a student. Anyone with the token can see the credential, so the token is
 * random and unguessable. It never expires, and a student has one per skill.
 */
@Entity
@Table(name = "shareable_credentials")
public class ShareableCredential {

    public static final int MAX_SKILL_TAG_LENGTH = 100;
    /** 32 random bytes in base64url without padding. */
    public static final int TOKEN_LENGTH = 43;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "owner_id", nullable = false)
    private int ownerId;

    @Column(name = "skill_tag", nullable = false, length = MAX_SKILL_TAG_LENGTH)
    private String skillTag;

    @Column(name = "token", nullable = false, length = 64, updatable = false)
    private String token;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected ShareableCredential() {
    }

    /** @throws DomainException when the owner, the skill or the token are not valid */
    public ShareableCredential(int ownerId, String skillTag, String token, Instant createdAt) {
        if (ownerId <= 0) {
            throw new DomainException("The credential must belong to a valid user.");
        }
        if (skillTag == null || skillTag.isBlank()) {
            throw new DomainException("The skill of the credential is required.");
        }
        if (skillTag.strip().length() > MAX_SKILL_TAG_LENGTH) {
            throw new DomainException("The skill cannot be longer than " + MAX_SKILL_TAG_LENGTH + " characters.");
        }
        if (token == null || token.length() != TOKEN_LENGTH || !token.matches("[A-Za-z0-9_-]+")) {
            throw new DomainException("The token of the credential is not valid.");
        }

        this.ownerId = ownerId;
        this.skillTag = skillTag.strip();
        this.token = token;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public Integer getId() {
        return id;
    }

    public int getOwnerId() {
        return ownerId;
    }

    public String getSkillTag() {
        return skillTag;
    }

    public String getToken() {
        return token;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
