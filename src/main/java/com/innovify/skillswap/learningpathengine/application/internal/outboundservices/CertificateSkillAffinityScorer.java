package com.innovify.skillswap.learningpathengine.application.internal.outboundservices;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillAffinity;
import java.util.Collection;
import java.util.List;

/**
 * Compares the content of a certificate with skills of the taxonomy, decoupling the application from the
 * comparison technique (keywords of the catalog today).
 */
public interface CertificateSkillAffinityScorer {

    /**
     * The affinity between the certificate and each of the given skills, in the order received. A skill that is
     * not in the taxonomy gets 0.
     */
    List<SkillAffinity> score(CertificateContent certificate, Collection<String> skillTags);
}
