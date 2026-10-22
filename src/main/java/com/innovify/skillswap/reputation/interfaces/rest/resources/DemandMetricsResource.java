package com.innovify.skillswap.reputation.interfaces.rest.resources;

import java.time.LocalDate;
import java.util.List;

/** What the students asked for in the period (from and to inclusive). */
public record DemandMetricsResource(LocalDate from, LocalDate to, List<SkillDemandResource> topSkills,
                                    List<CertificationDemandResource> topCertifications) {

    /** A skill and how many learning paths asked for it. */
    public record SkillDemandResource(String skillTag, String skillName, int pathCount) {
    }

    /** A course and how many certificates of it were uploaded. */
    public record CertificationDemandResource(String courseName, int count) {
    }
}
