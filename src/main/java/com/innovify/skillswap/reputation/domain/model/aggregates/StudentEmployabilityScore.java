package com.innovify.skillswap.reputation.domain.model.aggregates;

import com.innovify.skillswap.reputation.domain.model.valueobjects.EmployabilityScore;
import com.innovify.skillswap.reputation.domain.services.EmployabilityScoreCalculator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.Objects;

/**
 * The employability a student demonstrated through the skills they certified, whether the node was approved
 * automatically or by a verifier. The score is recalculated with every certified skill.
 */
public class StudentEmployabilityScore {

    private Integer id;
    private int studentId;
    private int verifiedSkillsCount;
    private EmployabilityScore score;
    private Instant updatedAt;

    /** Required by JPA once the aggregate is mapped. */
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
