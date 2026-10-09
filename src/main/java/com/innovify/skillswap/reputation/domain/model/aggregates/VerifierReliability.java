package com.innovify.skillswap.reputation.domain.model.aggregates;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.reputation.domain.services.VerifierReliabilityCalculator;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.Objects;

/**
 * The accumulated reliability of a verifier. It only changes through events of other bounded contexts, never
 * because a user rates another, and every change recalculates the score so it always agrees with the counters.
 */
public class VerifierReliability {

    private Integer id;
    private int verifierUserId;
    private int resolvedCasesCount;
    private int overturnedDecisionsCount;
    private int sanctionsCount;
    private ReliabilityScore score;
    private Instant updatedAt;

    /** Required by JPA once the aggregate is mapped. */
    protected VerifierReliability() {
    }

    /** @throws DomainException when the user is not valid */
    public VerifierReliability(int verifierUserId) {
        if (verifierUserId <= 0) {
            throw new DomainException("The reliability must belong to a valid user.");
        }

        this.verifierUserId = verifierUserId;
        this.score = new ReliabilityScore(ReliabilityScore.MAX);
        this.updatedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public int getVerifierUserId() {
        return verifierUserId;
    }

    public int getResolvedCasesCount() {
        return resolvedCasesCount;
    }

    public int getOverturnedDecisionsCount() {
        return overturnedDecisionsCount;
    }

    public int getSanctionsCount() {
        return sanctionsCount;
    }

    public ReliabilityScore getScore() {
        return score;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The verifier resolved a case, approving or rejecting it. */
    public VerifierReliability recordResolution(VerifierReliabilityCalculator calculator) {
        Objects.requireNonNull(calculator, "calculator");
        resolvedCasesCount++;
        return recalculate(calculator);
    }

    /** A decision of the verifier was overturned after an appeal. */
    public VerifierReliability recordOverturn(VerifierReliabilityCalculator calculator) {
        Objects.requireNonNull(calculator, "calculator");
        overturnedDecisionsCount++;
        return recalculate(calculator);
    }

    /** A sanction was applied to the verifier's account, the heaviest penalty of the model. */
    public VerifierReliability applySanction(VerifierReliabilityCalculator calculator) {
        Objects.requireNonNull(calculator, "calculator");
        sanctionsCount++;
        return recalculate(calculator);
    }

    private VerifierReliability recalculate(VerifierReliabilityCalculator calculator) {
        this.score = calculator.calculate(resolvedCasesCount, overturnedDecisionsCount, sanctionsCount);
        this.updatedAt = Instant.now();
        return this;
    }
}
