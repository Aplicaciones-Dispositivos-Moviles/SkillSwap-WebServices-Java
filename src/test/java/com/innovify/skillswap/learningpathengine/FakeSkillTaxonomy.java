package com.innovify.skillswap.learningpathengine;

import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import java.util.Map;

/** A taxonomy defined by a map of skill to direct prerequisites. */
public class FakeSkillTaxonomy implements SkillTaxonomy {

    private final Map<String, List<String>> prerequisites;

    public FakeSkillTaxonomy(Map<String, List<String>> prerequisites) {
        this.prerequisites = prerequisites;
    }

    /**
     * programming-fundamentals and networking-basics -> http-basics -> rest-api-design -> authentication-jwt
     * (rest-api-design also requires programming-fundamentals), plus the independent sql-fundamentals.
     */
    public static FakeSkillTaxonomy sample() {
        return new FakeSkillTaxonomy(Map.of(
                "programming-fundamentals", List.of(),
                "networking-basics", List.of(),
                "http-basics", List.of("networking-basics"),
                "rest-api-design", List.of("http-basics", "programming-fundamentals"),
                "authentication-jwt", List.of("rest-api-design"),
                "sql-fundamentals", List.of()));
    }

    @Override
    public boolean contains(String skillTag) {
        return prerequisites.containsKey(skillTag);
    }

    @Override
    public List<String> prerequisitesOf(String skillTag) {
        List<String> result = prerequisites.get(skillTag);
        if (result == null) {
            throw new DomainException("The skill '%s' is not in the taxonomy.".formatted(skillTag));
        }
        return result;
    }

    @Override
    public String nameOf(String skillTag) {
        return skillTag;
    }
}
