package com.innovify.skillswap.reputation.application.queryservices;

import java.time.LocalDate;
import java.util.List;

/**
 * What the students asked for in a period.
 *
 * @param from              first day of the period (inclusive)
 * @param to                last day of the period (inclusive)
 * @param topSkills         the skills most asked for by learning paths, the most requested first
 * @param topCertifications the certifications most uploaded, the most frequent first
 */
public record DemandMetrics(LocalDate from, LocalDate to, List<SkillDemand> topSkills,
                            List<CertificationDemand> topCertifications) {

    /** A skill and how many paths asked for it. */
    public record SkillDemand(String skillTag, String skillName, int pathCount) {
    }

    /** A course and how many certificates of it were uploaded. */
    public record CertificationDemand(String courseName, int count) {
    }
}
