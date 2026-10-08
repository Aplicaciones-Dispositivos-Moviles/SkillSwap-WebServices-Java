package com.innovify.skillswap.learningpathengine.infrastructure.taxonomy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class SkillTaxonomyTest {

    private static final SkillCatalog CATALOG = SkillCatalog.loadEmbedded();
    private static final KeywordSkillTaxonomyMatcher MATCHER = new KeywordSkillTaxonomyMatcher(CATALOG);
    private static final JsonSkillTaxonomy TAXONOMY = new JsonSkillTaxonomy(CATALOG);

    private static List<String> sorted(List<String> tags) {
        return tags.stream().sorted().toList();
    }

    // ---------- Loading the embedded catalog ----------

    @Test
    void embeddedCatalog_loadsAndIsNotEmpty() {
        assertThat(CATALOG.skills().size()).isGreaterThanOrEqualTo(40);
        assertThat(CATALOG.find("rest-api-design")).hasValueSatisfying(
                skill -> assertThat(skill.name()).isEqualTo("REST API design"));
    }

    // ---------- Parsing and validation ----------

    private static String json(String skills) {
        return "{\"version\": 1, \"skills\": [" + skills + "]}";
    }

    private static String skill(String tag, String prerequisites, String keywords, String name) {
        return "{\"tag\": \"" + tag + "\", \"name\": \"" + name + "\", \"category\": \"web\", \"keywords\": ["
                + keywords + "], \"prerequisites\": [" + prerequisites + "]}";
    }

    private static String skill(String tag) {
        return skill(tag, "", "\"kw\"", "Name");
    }

    @Test
    void parse_withAValidCatalog_succeeds() {
        SkillCatalog catalog = SkillCatalog.parse(
                json(skill("a") + "," + skill("b", "\"a\"", "\"other\"", "Name")));

        assertThat(catalog.skills()).hasSize(2);
    }

    @Test
    void parse_ignoresAByteOrderMark() {
        SkillCatalog catalog = SkillCatalog.parse("﻿" + json(skill("a")));

        assertThat(catalog.skills()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not json", "{\"version\": 1, \"skills\": []}", "{\"version\": 1}"})
    void parse_withAnUnusableDocument_throws(String json) {
        assertThatThrownBy(() -> SkillCatalog.parse(json)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parse_withARepeatedTag_throws() {
        assertThatThrownBy(() -> SkillCatalog.parse(json(skill("a") + "," + skill("a", "", "\"other\"", "Name"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("repeated");
    }

    @Test
    void parse_withAnUnknownPrerequisite_throws() {
        assertThatThrownBy(() -> SkillCatalog.parse(json(skill("a", "\"ghost\"", "\"kw\"", "Name"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void parse_withASkillThatRequiresItself_throws() {
        assertThatThrownBy(() -> SkillCatalog.parse(json(skill("a", "\"a\"", "\"kw\"", "Name"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parse_withAPrerequisiteCycle_throws() {
        String cyclic = json(skill("a", "\"b\"", "\"kw\"", "Name") + "," + skill("b", "\"a\"", "\"other\"", "Name"));

        assertThatThrownBy(() -> SkillCatalog.parse(cyclic))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cycle");
    }

    @Test
    void parse_withASkillWithoutUsableKeywords_throws() {
        assertThatThrownBy(() -> SkillCatalog.parse(json(skill("a", "", "\"!!!\", \" \"", "Name"))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void parse_withABlankName_throws() {
        assertThatThrownBy(() -> SkillCatalog.parse(json(skill("a", "", "\"kw\"", " "))))
                .isInstanceOf(IllegalStateException.class);
    }

    // ---------- Taxonomy ----------

    @Test
    void taxonomy_exposesTheDirectPrerequisites() {
        assertThat(TAXONOMY.contains("rest-api-design")).isTrue();
        assertThat(TAXONOMY.prerequisitesOf("rest-api-design"))
                .containsExactly("http-basics", "programming-fundamentals");
        assertThat(TAXONOMY.prerequisitesOf("git-version-control")).isEmpty();
    }

    @Test
    void taxonomy_withAnUnknownSkill_throwsAndDoesNotContainIt() {
        assertThat(TAXONOMY.contains("cooking")).isFalse();
        assertThatThrownBy(() -> TAXONOMY.prerequisitesOf("cooking")).isInstanceOf(DomainException.class);
    }

    @Test
    void taxonomy_exposesTheDisplayNameAndFallsBackToTheTagForUnknownSkills() {
        assertThat(TAXONOMY.nameOf("rest-api-design")).isEqualTo("REST API design");
        assertThat(TAXONOMY.nameOf("cooking")).isEqualTo("cooking");
    }

    // ---------- Matcher: goals that must be understood ----------

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "quiero aprender a construir APIs REST con autenticación JWT|authentication-jwt,rest-api-design",
            "quiero ser analista de datos con Power BI|data-analysis-bi",
            "quiero desarrollar apps móviles con Flutter|flutter,mobile-app-fundamentals",
            "Quiero ser desarrollador backend en ASP.NET Core y PostgreSQL|aspnet-core,postgresql,rest-api-design",
            "quiero ser DBA y optimizar consultas|database-administration",
            "quiero aprender Spring Boot con Java|java-language,spring-boot",
            "I want to learn Docker and CI/CD|ci-cd-deployment,docker-containers",
            "quiero hacer pruebas unitarias|software-testing",
            "quiero aprender UX|ux-ui-design",
            "quiero programar en C#|csharp-language",
            "quiero hacer un login|authentication-jwt"
    })
    void match_findsTheSkillsOfTheGoal(String goal, String expectedTags) {
        assertThat(sorted(MATCHER.match(goal))).containsExactly(expectedTags.split(","));
    }

    @Test
    void match_ignoresCaseAndAccents() {
        assertThat(MATCHER.match("AUTENTICACIÓN")).containsExactly("authentication-jwt");
        assertThat(MATCHER.match("autenticacion")).containsExactly("authentication-jwt");
    }

    @Test
    void match_onlyMatchesWholeWords() {
        // "java" must not be found inside "javascript", nor "git" inside "digital".
        assertThat(MATCHER.match("quiero aprender JavaScript")).containsExactly("javascript");
        assertThat(MATCHER.match("transformación digital")).isEmpty();
    }

    // ---------- Matcher: ambiguous words must not match on their own ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "quiero cocinar pasteles",
            "quiero aprender seguridad",
            "quiero trabajar con la nube",
            "quiero aprender permisos",
            "quiero reservar una room",
            "quiero aprender excel",
            "quiero hacer testing",
            "quiero entrenar para un sprint",
            "",
            "   "
    })
    void match_doesNotInterpretAmbiguousOrUnrelatedText(String text) {
        assertThat(MATCHER.match(text)).isEmpty();
    }

    @Test
    void match_withNull_returnsNothing() {
        assertThat(MATCHER.match(null)).isEmpty();
    }

    @Test
    void match_isDeterministicAndFollowsTheCatalogOrder() {
        List<String> first = MATCHER.match("JWT y REST");
        List<String> second = MATCHER.match("JWT y REST");

        assertThat(first).isEqualTo(second);
        assertThat(first).containsExactly("rest-api-design", "authentication-jwt");
    }
}
