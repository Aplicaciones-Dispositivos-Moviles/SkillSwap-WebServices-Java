package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateContent;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.CertificateSkillAffinityScorer;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import java.util.Collection;
import java.util.List;

/**
 * Scores with a phrase matcher: 1.0 when the course name matches the skill, 0.75 when only the OCR text does, 0
 * otherwise. With the sample matcher, "REST" covers rest-api-design, "HTTP" http-basics and "JWT"
 * authentication-jwt.
 */
public class FakeCertificateSkillAffinityScorer implements CertificateSkillAffinityScorer {

    private final SkillTaxonomyMatcher matcher;

    public FakeCertificateSkillAffinityScorer(SkillTaxonomyMatcher matcher) {
        this.matcher = matcher;
    }

    public static FakeCertificateSkillAffinityScorer sample() {
        return new FakeCertificateSkillAffinityScorer(FakeSkillTaxonomyMatcher.sample());
    }

    @Override
    public List<SkillAffinity> score(CertificateContent certificate, Collection<String> skillTags) {
        List<String> byCourse = matcher.match(certificate.courseName() == null ? "" : certificate.courseName());
        List<String> byText = matcher.match(certificate.ocrText() == null ? "" : certificate.ocrText());
        return skillTags.stream()
                .map(tag -> new SkillAffinity(tag, byCourse.contains(tag) ? 1.0 : byText.contains(tag) ? 0.75 : 0.0))
                .toList();
    }
}
