package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;

/** {@link SkillTaxonomy} backed by the JSON skill catalog. */
public class JsonSkillTaxonomy implements SkillTaxonomy {

    private final SkillCatalog catalog;

    public JsonSkillTaxonomy(SkillCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean contains(String skillTag) {
        return catalog.find(skillTag).isPresent();
    }

    @Override
    public List<String> prerequisitesOf(String skillTag) {
        return catalog.find(skillTag)
                .map(SkillDefinition::prerequisites)
                .orElseThrow(() -> new DomainException("The skill '%s' is not in the taxonomy.".formatted(skillTag)));
    }

    @Override
    public String nameOf(String skillTag) {
        return catalog.find(skillTag).map(SkillDefinition::name).orElse(skillTag);
    }
}
