package com.innovify.skillswap.reputation.domain.model.aggregates;

import com.innovify.skillswap.reputation.domain.model.valueobjects.EmployabilityScore;
import com.innovify.skillswap.reputation.domain.services.EmployabilityScoreCalculator;
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
 * The employability a student demonstrated through the skills they certified, whether the node was approved
 * automatically or by a verifier. The score is recalculated with every certified skill.
 */
@Entity
@Table(name = "student_employability_scores")
public class StudentEmployabilityScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "verified_skills_count", nullable = false)
    private int verifiedSkillsCount;

    @Column(name = "score", nullable = false)
    private EmployabilityScore score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected StudentEmployabilityScore() {
    }

    /** @throws DomainException when the student is not valid */
    public StudentEmployabilityScore(int studentId) {
        if (studentId <= 0) {
            throw new DomainException("The score must belong to a valid student.");
        }

        this.studentId = studentId;
        this.score = new EmployabilityScore(EmployabilityScore.MIN);
        this.updatedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getVerifiedSkillsCount() {
        return verifiedSkillsCount;
    }

    public EmployabilityScore getScore() {
        return score;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The student certified one more skill. */
    public StudentEmployabilityScore recordSkillVerified(EmployabilityScoreCalculator calculator) {
        Objects.requireNonNull(calculator, "calculator");
        verifiedSkillsCount++;
        this.score = calculator.calculate(verifiedSkillsCount);
        this.updatedAt = Instant.now();
        return this;
    }
}
