package com.innovify.skillswap.learningpathengine.application.acl;

import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SkillCatalogContextFacadeImpl implements SkillCatalogContextFacade {

    private final SkillTaxonomyMatcher matcher;

    public SkillCatalogContextFacadeImpl(SkillTaxonomyMatcher matcher) {
        this.matcher = matcher;
    }

    @Override
    public List<String> matchSkillTags(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return matcher.match(text).stream().distinct().toList();
    }
}
