package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Topological order of the missing skills. Skills that become ready together are placed in alphabetical order,
 * so the same gap always gives the same path. Only prerequisites that are part of the path matter: the others
 * were already demonstrated.
 */
public class DefaultLearningPathBuilder implements LearningPathBuilder {

    private final SkillTaxonomy taxonomy;

    public DefaultLearningPathBuilder(SkillTaxonomy taxonomy) {
        this.taxonomy = taxonomy;
    }

    @Override
    public List<PathNode> buildPath(SkillGap gap) {
        Set<String> missing = new HashSet<>(gap.missingSkillTags());

        Map<String, List<String>> inPathPrerequisites = new LinkedHashMap<>();
        for (String tag : gap.missingSkillTags()) {
            inPathPrerequisites.put(tag, taxonomy.prerequisitesOf(tag).stream().filter(missing::contains).toList());
        }

        List<String> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        while (placed.size() < missing.size()) {
            List<String> ready = missing.stream()
                    .filter(tag -> !placed.contains(tag) && placed.containsAll(inPathPrerequisites.get(tag)))
                    .sorted()
                    .toList();
            if (ready.isEmpty()) {
                throw new DomainException("The skill taxonomy contains a prerequisite cycle.");
            }
            ordered.addAll(ready);
            placed.addAll(ready);
        }

        List<PathNode> nodes = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            String tag = ordered.get(index);
            nodes.add(new PathNode(tag, index + 1, inPathPrerequisites.get(tag)));
        }
        return nodes;
    }
}
