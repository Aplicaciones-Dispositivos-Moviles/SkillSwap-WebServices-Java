package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Guards the integrity of skill-catalog.json: any edit that breaks the taxonomy fails here. */
class SkillCatalogTest {

    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of("fundamentals", "web", "backend", "devops", "database", "data", "design", "mobile");

    /** Keywords too ambiguous to identify a skill on their own (compared after normalization). */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "seguridad", "security", "excel", "nube", "cloud", "permisos", "room", "offline", "sprint", "agile",
            "agil", "testing", "solid", "terminal", "shell", "spring", "node", "express", "contenedores",
            "containers", "deploy", "deployment", "despliegue", "localizacion", "persistencia", "commits",
            "dashboards", "widgets", "multiplataforma", "dio", "qa", "aria", "backup", "respaldo", "indices",
            "triggers", "normalizacion", "camara", "sensores", "gps");

    private static final Pattern KEBAB_CASE = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private static final List<SkillDefinition> SKILLS = SkillCatalog.loadEmbedded().skills();

    @Test
    void catalog_isNotEmpty() {
        assertThat(SKILLS.size()).isGreaterThanOrEqualTo(40);
    }

    @Test
    void tags_areUniqueAndLowercaseKebabCase() {
        assertThat(SKILLS.stream().map(SkillDefinition::tag).distinct().count()).isEqualTo(SKILLS.size());
        assertThat(SKILLS).allSatisfy(skill -> assertThat(skill.tag()).matches(KEBAB_CASE));
    }

    @Test
    void everySkill_hasANameAndAnAllowedCategory() {
        assertThat(SKILLS).allSatisfy(skill -> {
            assertThat(skill.name()).as(skill.tag()).isNotBlank();
            assertThat(skill.category()).as(skill.tag()).isIn(ALLOWED_CATEGORIES);
        });
    }

    @Test
    void prerequisites_existAndAreNotTheSkillItself() {
        Set<String> tags = new HashSet<>();
        SKILLS.forEach(skill -> tags.add(skill.tag()));

        assertThat(SKILLS).allSatisfy(skill -> {
            assertThat(skill.prerequisites()).doesNotContain(skill.tag());
            assertThat(skill.prerequisites()).as(skill.tag()).isSubsetOf(tags);
            assertThat(skill.prerequisites()).doesNotHaveDuplicates();
        });
    }

    @Test
    void prerequisites_formNoCycle() {
        Map<String, SkillDefinition> byTag = new HashMap<>();
        SKILLS.forEach(skill -> byTag.put(skill.tag(), skill));
        Map<String, Integer> state = new HashMap<>();

        for (SkillDefinition skill : SKILLS) {
            assertThat(hasCycleFrom(skill.tag(), byTag, state)).as("cycle through " + skill.tag()).isFalse();
        }
    }

    private static boolean hasCycleFrom(String tag, Map<String, SkillDefinition> byTag,
                                        Map<String, Integer> state) {
        Integer current = state.get(tag);
        if (current != null) {
            return current == 1;
        }
        state.put(tag, 1);
        for (String prerequisite : byTag.get(tag).prerequisites()) {
            if (hasCycleFrom(prerequisite, byTag, state)) {
                return true;
            }
        }
        state.put(tag, 2);
        return false;
    }

    @Test
    void everySkill_hasKeywords_andNoneIsBlankAfterNormalization() {
        assertThat(SKILLS).allSatisfy(skill -> {
            assertThat(skill.keywords()).as(skill.tag()).isNotEmpty();
            assertThat(skill.keywords()).allSatisfy(
                    keyword -> assertThat(TextNormalizer.normalize(keyword)).as(skill.tag() + ": '" + keyword + "'")
                            .isNotBlank());
            assertThat(skill.keywords().stream().map(TextNormalizer::normalize).distinct().count())
                    .as(skill.tag())
                    .isEqualTo(skill.keywords().size());
        });
    }

    @Test
    void keywords_areNotSharedBetweenSkills() {
        Map<String, Set<String>> tagsByKeyword = new HashMap<>();
        for (SkillDefinition skill : SKILLS) {
            for (String keyword : skill.keywords()) {
                tagsByKeyword.computeIfAbsent(TextNormalizer.normalize(keyword), key -> new TreeSet<>())
                        .add(skill.tag());
            }
        }

        List<String> shared = new ArrayList<>();
        tagsByKeyword.forEach((keyword, tags) -> {
            if (tags.size() > 1) {
                shared.add("'" + keyword + "' in " + String.join(", ", tags));
            }
        });

        assertThat(shared).isEmpty();
    }

    @Test
    void keywords_doNotIncludeTooAmbiguousWords() {
        List<String> forbidden = new ArrayList<>();
        for (SkillDefinition skill : SKILLS) {
            for (String keyword : skill.keywords()) {
                if (FORBIDDEN_KEYWORDS.contains(TextNormalizer.normalize(keyword))) {
                    forbidden.add(skill.tag() + ": '" + keyword + "'");
                }
            }
        }

        assertThat(forbidden).isEmpty();
    }
}
