package com.innovify.skillswap.reputation.domain.model.aggregates;

import com.innovify.skillswap.reputation.domain.model.valueobjects.ReliabilityScore;
import com.innovify.skillswap.reputation.domain.model.valueobjects.VerifierRank;
import com.innovify.skillswap.reputation.domain.services.SeniorVerifierPolicy;
import com.innovify.skillswap.reputation.domain.services.VerifierReliabilityCalculator;
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
 * The accumulated reliability of a verifier. It only changes through events of other bounded contexts, never
 * because a user rates another, and every change recalculates the score so it always agrees with the counters.
 */
@Entity
@Table(name = "verifier_reliabilities")
public class VerifierReliability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "verifier_user_id", nullable = false)
    private int verifierUserId;

    @Column(name = "resolved_cases_count", nullable = false)
    private int resolvedCasesCount;

    @Column(name = "overturned_decisions_count", nullable = false)
    private int overturnedDecisionsCount;

    @Column(name = "sanctions_count", nullable = false)
    private int sanctionsCount;

    /** The cases the verifier did not resolve within their deadline (US39). */
    @Column(name = "missed_deadlines_count", nullable = false)
    private int missedDeadlinesCount;

    @Column(name = "score", nullable = false)
    private ReliabilityScore score;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
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

    public int getMissedDeadlinesCount() {
        return missedDeadlinesCount;
    }

    public ReliabilityScore getScore() {
        return score;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The rank reached with the resolved cases: Bronze, Silver or Gold. */
    public VerifierRank getRank() {
        return VerifierRank.fromResolvedCases(resolvedCasesCount);
    }

    /** Whether the verifier is a Verificador senior right now: Gold rank and a reliability of 90 or more. */
    public boolean isSeniorVerifier() {
        return SeniorVerifierPolicy.isSenior(resolvedCasesCount, score);
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

    /** The verifier let the deadline of an assigned case pass, so the case was taken from them. */
    public VerifierReliability recordMissedDeadline(VerifierReliabilityCalculator calculator) {
        Objects.requireNonNull(calculator, "calculator");
        missedDeadlinesCount++;
        return recalculate(calculator);
    }

    private VerifierReliability recalculate(VerifierReliabilityCalculator calculator) {
        this.score = calculator.calculate(resolvedCasesCount, overturnedDecisionsCount, sanctionsCount,
                missedDeadlinesCount);
        this.updatedAt = Instant.now();
        return this;
    }
}
