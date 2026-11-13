package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.application.commandservices.CertificateLinkOutcome;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.CertificateLinkResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.PathNodeResource;
import java.util.function.Function;

public final class CertificateLinkResourceFromOutcomeAssembler {

    private CertificateLinkResourceFromOutcomeAssembler() {
    }

    public static CertificateLinkResource toResourceFromOutcome(CertificateLinkOutcome outcome,
                                                                Function<String, String> skillNameOf) {
        PathNodeResource node = LearningPathResourceFromEntityAssembler.toResourceFromEntity(outcome.path(),
                        skillNameOf).nodes().stream()
                .filter(candidate -> candidate.id() == outcome.pathNodeId())
                .findFirst()
                .orElseThrow();
        boolean assessmentEnabled = outcome.path().isActive()
                && NodeStatus.AVAILABLE.value().equals(node.status());
        return new CertificateLinkResource(outcome.pathNodeId(), outcome.certificateId(), outcome.affinity(),
                SkillAffinity.COVERAGE_THRESHOLD, assessmentEnabled, node);
    }
}
