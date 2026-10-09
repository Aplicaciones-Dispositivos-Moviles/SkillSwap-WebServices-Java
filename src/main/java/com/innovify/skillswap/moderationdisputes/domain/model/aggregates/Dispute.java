package com.innovify.skillswap.moderationdisputes.domain.model.aggregates;

import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeOutcome;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeStatus;
import com.innovify.skillswap.moderationdisputes.domain.services.DisputeResolutionValidator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A case that needs the final decision of a Verificador senior, whatever its origin. It references the certificate
 * or the case under review by id only, so this context never depends on their models. Today the only origin is a
 * certificate that Credential Verification registered as suspicious; it is opened by the system (nobody raised it)
 * and its respondent is the owner of the certificate.
 *
 * <p>The dispute is assigned to one reviewer, who resolves it with an outcome valid for its origin and their
 * observations. The respondent and whoever raised it can never review it.
 */
@Entity
@Table(name = "disputes")
public class Dispute {

    public static final int MAX_REASON_LENGTH = 500;
    public static final int MAX_RESOLUTION_NOTES_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "source_type", nullable = false, length = 40)
    private DisputeSourceType sourceType;

    /** The id of the certificate (or case) under review, according to the source type. */
    @Column(name = "source_reference_id", nullable = false)
    private int sourceReferenceId;

    /** Null when the system opened it. */
    @Column(name = "raised_by_user_id")
    private Integer raisedByUserId;

    /** The user whose certificate, decision or behavior is questioned. */
    @Column(name = "respondent_user_id")
    private Integer respondentUserId;

    @Column(name = "reason", nullable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "status", nullable = false, length = 20)
    private DisputeStatus status;

    @Column(name = "outcome", length = 20)
    private DisputeOutcome outcome;

    @Column(name = "resolution_notes", length = MAX_RESOLUTION_NOTES_LENGTH)
    private String resolutionNotes;

    @Column(name = "assigned_verifier_user_id")
    private Integer assignedVerifierUserId;

    /** Whether the reviewer was a Verificador senior when it was assigned, or the fallback verifier. */
    @Column(name = "assigned_to_senior", nullable = false)
    private boolean assignedToSenior;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "raised_at", nullable = false)
    private Instant raisedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** Required by JPA. */
    protected Dispute() {
    }

    /**
     * Opens a pending dispute without a reviewer.
     *
     * @throws DomainException when the origin is missing, an id is not valid or the reason is empty or too long
     */
    public Dispute(DisputeSourceType sourceType, int sourceReferenceId, Integer raisedByUserId,
                   Integer respondentUserId, String reason) {
        if (sourceType == null) {
            throw new DomainException("The origin of the dispute is required.");
        }
        if (sourceReferenceId <= 0) {
            throw new DomainException("The dispute must reference a valid certificate or case.");
        }
        if ((raisedByUserId != null && raisedByUserId <= 0) || (respondentUserId != null && respondentUserId <= 0)) {
            throw new DomainException("The parties of the dispute must be valid users.");
        }
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty()) {
            throw new DomainException("The reason of the dispute cannot be empty.");
        }
        if (text.length() > MAX_REASON_LENGTH) {
            throw new DomainException("The reason cannot exceed %d characters.".formatted(MAX_REASON_LENGTH));
        }

        this.sourceType = sourceType;
        this.sourceReferenceId = sourceReferenceId;
        this.raisedByUserId = raisedByUserId;
        this.respondentUserId = respondentUserId;
        this.reason = text;
        this.status = DisputeStatus.PENDING;
        this.raisedAt = Instant.now();
    }

    /**
     * The escalation of a suspicious certificate, opened by the system against its owner.
     *
     * @param reasons the rules that made the certificate suspicious; "HighRisk" when none is given
     */
    public static Dispute certificateReview(int certificateId, int ownerId, List<String> reasons) {
        String reason = reasons == null || reasons.isEmpty() ? "HighRisk" : String.join(", ", reasons);
        return new Dispute(DisputeSourceType.CERTIFICATE_REVIEW, certificateId, null, ownerId, reason);
    }

    /**
     * Gives the dispute to its reviewer.
     *
     * @param senior whether the reviewer is a Verificador senior (false for the fallback verifier)
     * @throws DomainException when the dispute is not pending, already has a reviewer, or the reviewer is not valid
     *                         or is a party of the dispute
     */
    public Dispute assignReviewer(int verifierUserId, boolean senior) {
        if (status != DisputeStatus.PENDING) {
            throw new DomainException("Only a pending dispute can be assigned.");
        }
        if (assignedVerifierUserId != null) {
            throw new DomainException("The dispute already has a reviewer.");
        }
        if (verifierUserId <= 0) {
            throw new DomainException("The reviewer must be a valid user.");
        }
        if (isParty(verifierUserId)) {
            throw new DomainException("A party of the dispute cannot review it.");
        }

        this.assignedVerifierUserId = verifierUserId;
        this.assignedToSenior = senior;
        this.assignedAt = Instant.now();
        return this;
    }

    /**
     * Records the decision of the reviewer and closes the dispute.
     *
     * @throws DomainException when the dispute cannot be resolved, has no reviewer, the outcome does not apply to its
     *                         origin, or the observations are empty or too long
     */
    public Dispute resolve(DisputeOutcome outcome, String resolutionNotes, DisputeResolutionValidator validator) {
        Objects.requireNonNull(validator, "validator");
        if (!validator.canResolve(this)) {
            throw new DomainException("The dispute is already resolved.");
        }
        if (assignedVerifierUserId == null) {
            throw new DomainException("A dispute needs a reviewer before it can be resolved.");
        }
        if (!validator.isValidOutcome(sourceType, outcome)) {
            throw new DomainException("The outcome does not apply to this kind of dispute.");
        }
        String notes = resolutionNotes == null ? "" : resolutionNotes.strip();
        if (notes.isEmpty()) {
            throw new DomainException("The observations of the reviewer are required.");
        }
        if (notes.length() > MAX_RESOLUTION_NOTES_LENGTH) {
            throw new DomainException(
                    "The observations cannot exceed %d characters.".formatted(MAX_RESOLUTION_NOTES_LENGTH));
        }

        this.outcome = outcome;
        this.resolutionNotes = notes;
        this.status = DisputeStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        return this;
    }

    /** Whether the user is the respondent or raised the dispute. */
    public boolean isParty(int userId) {
        return (respondentUserId != null && respondentUserId == userId)
                || (raisedByUserId != null && raisedByUserId == userId);
    }

    public boolean isAssignedTo(int userId) {
        return assignedVerifierUserId != null && assignedVerifierUserId == userId;
    }

    public boolean isPending() {
        return status == DisputeStatus.PENDING;
    }

    public boolean hasReviewer() {
        return assignedVerifierUserId != null;
    }

    /** Null until the dispute is persisted. */
    public Integer getId() {
        return id;
    }

    public DisputeSourceType getSourceType() {
        return sourceType;
    }

    public int getSourceReferenceId() {
        return sourceReferenceId;
    }

    public Integer getRaisedByUserId() {
        return raisedByUserId;
    }

    public Integer getRespondentUserId() {
        return respondentUserId;
    }

    public String getReason() {
        return reason;
    }

    public DisputeStatus getStatus() {
        return status;
    }

    /** Null until resolved. */
    public DisputeOutcome getOutcome() {
        return outcome;
    }

    /** Null until resolved. */
    public String getResolutionNotes() {
        return resolutionNotes;
    }

    /** Null while it waits for a reviewer. */
    public Integer getAssignedVerifierUserId() {
        return assignedVerifierUserId;
    }

    public boolean isAssignedToSenior() {
        return assignedToSenior;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getRaisedAt() {
        return raisedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
