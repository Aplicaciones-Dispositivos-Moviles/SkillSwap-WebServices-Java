package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.converters.StringListConverter;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Makes a student eligible to review the cases of other students in the skills they demonstrated themselves.
 * It can be switched off by the verifier (availability) or revoked by moderation.
 */
@Entity
@Table(name = "verifier_profiles")
public class VerifierProfile {

    public static final int MAX_SUSPENSION_REASON_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "verifier_user_id", nullable = false)
    private int verifierUserId;

    @Convert(converter = StringListConverter.class)
    @Column(name = "skill_tags", nullable = false, columnDefinition = "jsonb")
    private List<String> skillTags;

    @Column(name = "available", nullable = false)
    private boolean available;

    @Column(name = "verified", nullable = false)
    private boolean verified;

    @Column(name = "rating", nullable = false)
    private double rating;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "suspended", nullable = false)
    private boolean suspended;

    @Column(name = "suspended_at")
    private Instant suspendedAt;

    @Column(name = "suspension_reason", length = MAX_SUSPENSION_REASON_LENGTH)
    private String suspensionReason;

    @Column(name = "suspended_by")
    private Integer suspendedBy;

    /** Required by JPA. */
    protected VerifierProfile() {
    }

    /** @throws DomainException when the user is not valid or the skill is empty */
    public VerifierProfile(int verifierUserId, String skillTag) {
        if (verifierUserId <= 0) {
            throw new DomainException("The profile must belong to a valid user.");
        }

        this.verifierUserId = verifierUserId;
        this.skillTags = List.of(normalize(skillTag));
        this.available = true;
        this.verified = true;
        this.createdAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public int getVerifierUserId() {
        return verifierUserId;
    }

    /** The skills the verifier is enabled to review, sorted. */
    public List<String> getSkillTags() {
        return skillTags;
    }

    public boolean isAvailable() {
        return available;
    }

    /** Whether the profile is still enabled; moderation can revoke it. */
    public boolean isVerified() {
        return verified;
    }

    /** Reliability synchronized from Reputation; zero until it is first calculated. */
    public double getRating() {
        return rating;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Whether a Verificador senior suspended the verifier; it stays so until a senior reinstates them. */
    public boolean isSuspended() {
        return suspended;
    }

    public Instant getSuspendedAt() {
        return suspendedAt;
    }

    public String getSuspensionReason() {
        return suspensionReason;
    }

    /** The user id of the senior who suspended the verifier; null when it is not suspended. */
    public Integer getSuspendedBy() {
        return suspendedBy;
    }

    public boolean hasSkill(String skillTag) {
        return skillTags.contains(skillTag == null ? "" : skillTag.strip());
    }

    /**
     * Whether this verifier can take a case of the skill: still enabled, not suspended and enabled for that skill.
     */
    public boolean canReview(String skillTag) {
        return verified && !suspended && hasSkill(skillTag);
    }

    /**
     * Enables the verifier for one more skill.
     *
     * @return true when the skill was added; false when the verifier already had it
     */
    public boolean addSkill(String skillTag) {
        String tag = normalize(skillTag);
        if (skillTags.contains(tag)) {
            return false;
        }

        List<String> updated = new ArrayList<>(skillTags);
        updated.add(tag);
        updated.sort(String::compareTo);
        this.skillTags = List.copyOf(updated);
        return true;
    }

    public VerifierProfile setAvailability(boolean available) {
        this.available = available;
        return this;
    }

    public VerifierProfile incrementReviewCount() {
        this.reviewCount++;
        return this;
    }

    /** @throws DomainException when the rating is negative or not a number */
    public VerifierProfile updateRating(double rating) {
        if (Double.isNaN(rating) || rating < 0) {
            throw new DomainException("The rating cannot be negative.");
        }

        this.rating = rating;
        return this;
    }

    /**
     * Suspends the verifier: it receives no more cases until it is reinstated.
     *
     * @param suspendedByUserId the senior who suspends; never the verifier themselves
     * @throws DomainException when the verifier is already suspended, the senior is not valid or is the verifier, or
     *                         the reason is empty or longer than {@value #MAX_SUSPENSION_REASON_LENGTH} characters
     */
    public VerifierProfile suspend(int suspendedByUserId, String reason, Instant now) {
        if (suspended) {
            throw new DomainException("The verifier is already suspended.");
        }
        if (suspendedByUserId <= 0) {
            throw new DomainException("The suspension must be made by a valid user.");
        }
        if (suspendedByUserId == verifierUserId) {
            throw new DomainException("A verifier cannot suspend themselves.");
        }
        if (reason == null || reason.isBlank()) {
            throw new DomainException("The reason of the suspension is required.");
        }
        String stripped = reason.strip();
        if (stripped.length() > MAX_SUSPENSION_REASON_LENGTH) {
            throw new DomainException(
                    "The reason cannot exceed %d characters.".formatted(MAX_SUSPENSION_REASON_LENGTH));
        }
        if (now == null) {
            throw new DomainException("The moment of the suspension is required.");
        }

        this.suspended = true;
        this.suspendedAt = now;
        this.suspensionReason = stripped;
        this.suspendedBy = suspendedByUserId;
        return this;
    }

    /** @throws DomainException when the verifier is not suspended */
    public VerifierProfile reinstate() {
        if (!suspended) {
            throw new DomainException("The verifier is not suspended.");
        }

        this.suspended = false;
        this.suspendedAt = null;
        this.suspensionReason = null;
        this.suspendedBy = null;
        return this;
    }

    public VerifierProfile revoke() {
        this.verified = false;
        this.available = false;
        return this;
    }

    private static String normalize(String skillTag) {
        if (skillTag == null || skillTag.isBlank()) {
            throw new DomainException("The skill tag cannot be empty.");
        }
        return skillTag.strip();
    }
}
