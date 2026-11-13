package com.innovify.skillswap.reputation.interfaces.rest.transform;

import com.innovify.skillswap.reputation.application.queryservices.DemandMetrics;
import com.innovify.skillswap.reputation.application.queryservices.DifficultyMetrics;
import com.innovify.skillswap.reputation.application.queryservices.VerifierMetrics;
import com.innovify.skillswap.reputation.interfaces.rest.resources.DemandMetricsResource;
import com.innovify.skillswap.reputation.interfaces.rest.resources.DifficultyMetricsResource;
import com.innovify.skillswap.reputation.interfaces.rest.resources.VerifierMetricsResource;

/** Converts the platform metrics into REST resources. */
public final class PlatformMetricsResourceAssemblers {

    private PlatformMetricsResourceAssemblers() {
    }

    public static DemandMetricsResource toResource(DemandMetrics metrics) {
        return new DemandMetricsResource(metrics.from(), metrics.to(),
                metrics.topSkills().stream()
                        .map(skill -> new DemandMetricsResource.SkillDemandResource(skill.skillTag(),
                                skill.skillName(), skill.pathCount()))
                        .toList(),
                metrics.topCertifications().stream()
                        .map(certification -> new DemandMetricsResource.CertificationDemandResource(
                                certification.courseName(), certification.count()))
                        .toList());
    }

    public static DifficultyMetricsResource toResource(DifficultyMetrics metrics) {
        return new DifficultyMetricsResource(metrics.from(), metrics.to(), metrics.minAttempts(),
                metrics.skills().stream()
                        .map(skill -> new DifficultyMetricsResource.SkillDifficultyResource(skill.skillTag(),
                                skill.skillName(), skill.attempts(), skill.failed(), skill.failureRate()))
                        .toList(),
                metrics.subTopics().stream()
                        .map(subTopic -> new DifficultyMetricsResource.SubTopicDifficultyResource(
                                subTopic.skillTag(), subTopic.skillName(), subTopic.subTopic(),
                                subTopic.weakCount(), subTopic.shareOfFailures()))
                        .toList());
    }

    public static VerifierMetricsResource toResource(VerifierMetrics metrics) {
        return new VerifierMetricsResource(metrics.verifiers().stream()
                .map(verifier -> new VerifierMetricsResource.VerifierMetricResource(verifier.verifierUserId(),
                        verifier.fullName(), verifier.score(), verifier.rank(), verifier.resolvedCasesCount(),
                        verifier.overturnedDecisionsCount(), verifier.missedDeadlinesCount(),
                        verifier.seniorVerifier(), verifier.suspended(), verifier.averageResolutionHours()))
                .toList());
    }
}
