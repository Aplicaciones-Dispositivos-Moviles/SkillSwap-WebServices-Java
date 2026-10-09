package com.innovify.skillswap.reputation.interfaces.rest.transform;

import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.interfaces.rest.resources.StudentEmployabilityResource;
import com.innovify.skillswap.reputation.interfaces.rest.resources.VerifierReliabilityResource;

/** Converts the Reputation aggregates into REST resources. */
public final class ReputationResourceAssemblers {

    private ReputationResourceAssemblers() {
    }

    public static StudentEmployabilityResource toResource(StudentEmployabilityScore score) {
        return new StudentEmployabilityResource(score.getId(), score.getStudentId(), score.getVerifiedSkillsCount(),
                score.getScore().value(), score.getUpdatedAt());
    }

    public static VerifierReliabilityResource toResource(VerifierReliability reliability) {
        return new VerifierReliabilityResource(reliability.getId(), reliability.getVerifierUserId(),
                reliability.getResolvedCasesCount(), reliability.getOverturnedDecisionsCount(),
                reliability.getSanctionsCount(), reliability.getScore().value(), reliability.getUpdatedAt(),
                reliability.getRank().value(), reliability.isSeniorVerifier());
    }
}
