package com.innovify.skillswap.learningpathengine.domain.model.aggregates;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * The right to start one advanced learning path, granted when the student redeems the benefit with SkillCredits in
 * Recognition &amp; Incentives. It stays available until the student starts the advanced path with it; that path is
 * completed like any other, but it does not count toward the limits of the plan, whatever the plan.
 */
@Entity
@Table(name = "advanced_path_unlocks")
public class AdvancedPathUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    /** The redemption that paid it; it grants a single unlock. */
    @Column(name = "redemption_id", nullable = false)
    private int redemptionId;

    /** The advanced path started with it; null while it is available. */
    @Column(name = "learning_path_id")
    private Integer learningPathId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "used_at")
    private Instant usedAt;

    /** Required by JPA. */
    protected AdvancedPathUnlock() {
    }

    /** @throws DomainException when the student or the redemption are not valid */
    public AdvancedPathUnlock(int studentId, int redemptionId) {
        if (studentId <= 0) {
            throw new DomainException("The unlock must belong to a valid student.");
        }
        if (redemptionId <= 0) {
            throw new DomainException("The unlock must come from a valid redemption.");
        }
        this.studentId = studentId;
        this.redemptionId = redemptionId;
        this.grantedAt = Instant.now();
    }

    /**
     * Spends the unlock on the advanced path just started.
     *
     * @throws DomainException when it was already used or the path is not valid
     */
    public AdvancedPathUnlock useFor(int learningPathId) {
        if (!isAvailable()) {
            throw new DomainException("The advanced path unlock was already used.");
        }
        if (learningPathId <= 0) {
            throw new DomainException("The advanced path must be a valid path.");
        }
        this.learningPathId = learningPathId;
        this.usedAt = Instant.now();
        return this;
    }

    public boolean isAvailable() {
        return learningPathId == null;
    }

    public Integer getId() {
        return id;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getRedemptionId() {
        return redemptionId;
    }

    public Integer getLearningPathId() {
        return learningPathId;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
