package com.innovify.skillswap.learningpathengine.application.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.JsonSkillTaxonomy;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.KeywordSkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.infrastructure.taxonomy.SkillCatalog;
import org.junit.jupiter.api.Test;

/** Against the real embedded catalog, as the interest profile of IAM uses it. */
class SkillCatalogContextFacadeImplTest {

    private final SkillCatalogContextFacadeImpl facade = new SkillCatalogContextFacadeImpl(
            new KeywordSkillTaxonomyMatcher(SkillCatalog.loadEmbedded()),
            new JsonSkillTaxonomy(SkillCatalog.loadEmbedded()));

    @Test
    void matchSkillTags_relatesInterestTopicsToCatalogSkills() {
        assertThat(facade.matchSkillTags("Programación en Java")).contains("java-language");
        assertThat(facade.matchSkillTags("Desarrollo con React")).contains("react");
    }

    @Test
    void matchSkillTags_ofTextWithoutSkills_isEmpty() {
        assertThat(facade.matchSkillTags("Ajedrez y fútbol")).isEmpty();
        assertThat(facade.matchSkillTags("  ")).isEmpty();
        assertThat(facade.matchSkillTags(null)).isEmpty();
    }

    @Test
    void matchSkillTags_neverRepeatsATag() {
        assertThat(facade.matchSkillTags("java, Java y programación en java")).doesNotHaveDuplicates();
    }

    @Test
    void getSkillNames_givesTheDisplayNameOfEachSkillAndTheTagWhenUnknown() {
        var names = facade.getSkillNames(java.util.List.of("rest-api-design", "not-a-skill"));

        assertThat(names).containsEntry("rest-api-design", "REST API design").containsEntry("not-a-skill", "not-a-skill");
        assertThat(facade.getSkillNames(java.util.List.of())).isEmpty();
        assertThat(facade.getSkillNames(null)).isEmpty();
    }
}
