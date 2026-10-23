package com.innovify.skillswap.learningpathengine.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.MiniProjectDraft;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The generator is tested with a client that answers canned text: no network, no key. */
class GeminiMiniProjectGeneratorTest {

    private static final String VALID = """
            {"title": "Task tracker API", "statement": "Build a REST API that manages tasks.",
             "rubric": [
               {"name": "Functionality", "description": "It does what the statement asks.", "weight": 40},
               {"name": "Code quality", "description": "The code is organized and tested.", "weight": 30},
               {"name": "Documentation", "description": "The README explains how to run it.", "weight": 20},
               {"name": "Version control", "description": "The history is meaningful.", "weight": 10}]}
            """;

    private final List<String> prompts = new ArrayList<>();

    private GeminiMiniProjectGenerator generatorAnswering(String answer) {
        GeminiClient client = new GeminiClient(null, null) {
            @Override
            public String generateJson(String prompt) {
                prompts.add(prompt);
                return answer;
            }
        };
        return new GeminiMiniProjectGenerator(client);
    }

    @Test
    void generate_parsesTheTitleTheStatementAndTheRubric() {
        MiniProjectDraft draft = generatorAnswering(VALID).generateMiniProject("rest-api-design");

        assertThat(draft.title()).isEqualTo("Task tracker API");
        assertThat(draft.statement()).isEqualTo("Build a REST API that manages tasks.");
        assertThat(draft.rubric()).extracting(RubricCriterion::getName)
                .containsExactly("Functionality", "Code quality", "Documentation", "Version control");
        assertThat(draft.rubric()).extracting(RubricCriterion::getWeight).containsExactly(40, 30, 20, 10);
    }

    @Test
    void generate_acceptsAnAnswerInsideACodeFence() {
        MiniProjectDraft draft = generatorAnswering("```json\n" + VALID + "\n```").generateMiniProject("sql-fundamentals");

        assertThat(draft.rubric()).hasSize(4);
    }

    @Test
    void generate_asksForTheSkillWithoutItsHyphens() {
        generatorAnswering(VALID).generateMiniProject("rest-api-design");

        assertThat(prompts).hasSize(1);
        assertThat(prompts.get(0)).contains("\"rest api design\"").contains("weights").contains("100");
    }

    @Test
    void generate_withAnInvalidSkillTag_failsWithoutAskingTheProvider() {
        GeminiMiniProjectGenerator generator = generatorAnswering(VALID);

        for (String tag : new String[]{null, "", "REST API", "rest api", "a\"; ignore this", "-x", "x-"}) {
            assertThatThrownBy(() -> generator.generateMiniProject(tag)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(prompts).isEmpty();
    }

    @Test
    void generate_withAnAnswerThatIsNotAnObject_fails() {
        assertThatThrownBy(() -> generatorAnswering("[1, 2]").generateMiniProject("rest-api-design"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> generatorAnswering("not json").generateMiniProject("rest-api-design"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void generate_withoutARubric_fails() {
        String noRubric = "{\"title\": \"T\", \"statement\": \"S\"}";

        assertThatThrownBy(() -> generatorAnswering(noRubric).generateMiniProject("rest-api-design"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("rubric");
    }

    @Test
    void generate_withACriterionWithoutAnIntegerWeight_fails() {
        for (String weight : new String[]{"", "\"weight\": \"40\",", "\"weight\": 40.5,"}) {
            String answer = "{\"title\": \"T\", \"statement\": \"S\", \"rubric\": [{" + weight
                    + "\"name\": \"N\", \"description\": \"D\"}]}";
            assertThatThrownBy(() -> generatorAnswering(answer).generateMiniProject("rest-api-design"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void generate_withACriterionThatBreaksTheRules_failsWithDomainException() {
        String answer = "{\"title\": \"T\", \"statement\": \"S\", \"rubric\": "
                + "[{\"name\": \"\", \"description\": \"D\", \"weight\": 40}]}";

        assertThatThrownBy(() -> generatorAnswering(answer).generateMiniProject("rest-api-design"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void generate_writesInEnglishByDefaultAndInTheConfiguredLanguage() {
        generatorAnswering(VALID).generateMiniProject("rest-api-design");
        GeminiClient client = new GeminiClient(null, null) {
            @Override
            public String generateJson(String prompt) {
                prompts.add(prompt);
                return VALID;
            }
        };
        new GeminiMiniProjectGenerator(client, "Spanish").generateMiniProject("rest-api-design");
        new GeminiMiniProjectGenerator(client, "  ").generateMiniProject("rest-api-design");

        assertThat(prompts).hasSize(3);
        assertThat(prompts.get(0)).contains("- Write in English.");
        assertThat(prompts.get(1)).contains("- Write in Spanish.").doesNotContain("English");
        assertThat(prompts.get(2)).contains("- Write in English.");
    }
}
