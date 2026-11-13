package com.innovify.skillswap.learningpathengine.application.acl;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SkillCatalogContextFacadeImpl implements SkillCatalogContextFacade {

    private final SkillTaxonomyMatcher matcher;
    private final SkillTaxonomy taxonomy;

    public SkillCatalogContextFacadeImpl(SkillTaxonomyMatcher matcher, SkillTaxonomy taxonomy) {
        this.matcher = matcher;
        this.taxonomy = taxonomy;
    }

    @Override
    public List<String> matchSkillTags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return matcher.match(text).stream().distinct().toList();
    }

    @Override
    public Map<String, String> getSkillNames(Collection<String> skillTags) {
        Map<String, String> names = new LinkedHashMap<>();
        if (skillTags == null) {
            return names;
        }
        for (String skillTag : skillTags) {
            if (skillTag != null) {
                names.put(skillTag, taxonomy.nameOf(skillTag));
            }
        }
        return names;
    }
}
