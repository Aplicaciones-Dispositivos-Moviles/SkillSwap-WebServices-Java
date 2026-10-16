package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The record of a reevaluation ordered by a senior verifier: which approved result raised doubts, who ordered it and
 * why. The effects (the node available again, the case reopened) are applied by the application layer; this keeps
 * the justification that the student and the team can audit later.
 */
@Entity
@Table(name = "reevaluations")
public class Reevaluation {

    public static final int MAX_JUSTIFICATION_LENGTH = 2000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /** Stored as "Attempt" or "Case". */
    @Column(name = "source_type", nullable = false, length = 20)
    private ReevaluationSourceType sourceType;

    /** The id of the attempt or of the case, according to the source type. */
    @Column(name = "source_id", nullable = false)
    private int sourceId;

    /** The student whose result is evaluated again. */
    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "ordered_by_user_id", nullable = false)
    private int orderedByUserId;

    @Column(name = "justification", nullable = false, length = 2000)
    private String justification;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected Reevaluation() {
    }

    /**
     * @throws DomainException when the source type is missing, an id is not valid, the verifier is the student of the
     *                         result, or the justification is empty or too long
     */
    public Reevaluation(ReevaluationSourceType sourceType, int sourceId, int studentId, int orderedByUserId,
                        String justification) {
        if (sourceType == null) {
            throw new DomainException("The source type of the reevaluation is required.");
        }
        if (sourceId <= 0) {
            throw new DomainException("The reevaluation must refer to a valid attempt or case.");
        }
        if (studentId <= 0) {
            throw new DomainException("The reevaluation must refer to a valid student.");
        }
        if (orderedByUserId <= 0) {
            throw new DomainException("The reevaluation must be ordered by a valid user.");
        }
        if (orderedByUserId == studentId) {
            throw new DomainException("A verifier cannot order the reevaluation of their own result.");
        }

        String reason = justification == null ? "" : justification.strip();
        if (reason.isEmpty()) {
            throw new DomainException("The justification is required.");
        }
        if (reason.length() > MAX_JUSTIFICATION_LENGTH) {
            throw new DomainException(
                    "The justification cannot exceed %d characters.".formatted(MAX_JUSTIFICATION_LENGTH));
        }

        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.studentId = studentId;
        this.orderedByUserId = orderedByUserId;
        this.justification = reason;
        this.createdAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public ReevaluationSourceType getSourceType() {
        return sourceType;
    }

    public int getSourceId() {
        return sourceId;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getOrderedByUserId() {
        return orderedByUserId;
    }

    public String getJustification() {
        return justification;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
