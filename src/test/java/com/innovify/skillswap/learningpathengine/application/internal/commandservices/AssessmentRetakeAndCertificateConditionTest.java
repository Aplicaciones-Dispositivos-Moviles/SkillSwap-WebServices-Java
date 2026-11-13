package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeAssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeQuestionGenerationService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateAssessmentBlueprintCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * US17 escenario 3: a new attempt on a node never repeats the questions of the previous ones; and the optional
 * rule that the assessment of a node needs a linked certificate (US15 escenario 1). Node 1 of the seeded path is
 * networking-basics (available).
 */
class AssessmentRetakeAndCertificateConditionTest {

    private final FakeAssessmentBlueprintRepository blueprints = new FakeAssessmentBlueprintRepository();
    private final FakeQuestionGenerationService generator = new FakeQuestionGenerationService();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private ResourceBundleMessageSource messages;
    private AssessmentBlueprintCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        service = service(false);
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private AssessmentBlueprintCommandServiceImpl service(boolean requireLinkedCertificate) {
        return new AssessmentBlueprintCommandServiceImpl(paths, blueprints, generator,
                TransactionOperations.withoutTransaction(), messages, requireLinkedCertificate);
    }

    private Result<AssessmentBlueprint> generate() {
        return service.handle(new GenerateAssessmentBlueprintCommand(1, 1));
    }

    private static List<String> texts(AssessmentBlueprint blueprint) {
        return blueprint.getQuestions().stream().map(Question::getQuestionString).toList();
    }

    private LearningPath path() {
        return paths.paths().get(0);
    }

    // ---------- New attempts ----------

    @Test
    void firstAttempt_excludesNothing() {
        generate();

        assertThat(generator.exclusions()).containsExactly(List.of());
    }

    @Test
    @DisplayName("US17 escenario 3: the previous questions are sent as exclusions and none comes back")
    void newAttempt_sendsThePreviousQuestionsAndGetsDifferentOnes() {
        AssessmentBlueprint first = generate().value();

        AssessmentBlueprint second = generate().value();

        assertThat(generator.exclusions().get(1)).containsExactlyInAnyOrderElementsOf(texts(first));
        assertThat(texts(second)).doesNotContainAnyElementsOf(texts(first));
        assertThat(path().getNode(1).orElseThrow().getAssessmentBlueprintId()).isEqualTo(second.getId());
    }

    @Test
    void thirdAttempt_excludesTheQuestionsOfEveryPreviousAttempt() {
        AssessmentBlueprint first = generate().value();
        AssessmentBlueprint second = generate().value();

        AssessmentBlueprint third = generate().value();

        assertThat(generator.exclusions().get(2)).hasSize(10)
                .containsAll(texts(first)).containsAll(texts(second));
        assertThat(texts(third)).doesNotContainAnyElementsOf(texts(first)).doesNotContainAnyElementsOf(texts(second));
    }

    @Test
    @DisplayName("US17 escenario 3: when the AI repeats questions, they are replaced by asking again")
    void newAttempt_whenTheGeneratorRepeatsSomeQuestions_replacesThemAskingAgain() {
        AssessmentBlueprint first = generate().value();
        generator.repeatPreviousQuestions(1, 2);

        Result<AssessmentBlueprint> result = generate();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getQuestions()).hasSize(AssessmentBlueprint.QUESTION_COUNT);
        assertThat(texts(result.value())).doesNotContainAnyElementsOf(texts(first)).doesNotHaveDuplicates();
        assertThat(generator.requests()).hasSize(3);
        // The second request of the retake also excludes the fresh questions already kept.
        assertThat(generator.exclusions().get(2)).hasSize(8).containsAll(texts(first));
    }

    @Test
    void newAttempt_detectsARepeatedQuestionWrittenDifferently() {
        blueprints.save(new AssessmentBlueprint(1, "networking-basics", List.of(
                new Question("question 1", List.of("a", "b", "c", "d"), 0),
                TestData.question(91), TestData.question(92), TestData.question(93), TestData.question(94))));
        // The generator starts again at "Question 1?", the same question with other case and punctuation.

        AssessmentBlueprint retake = generate().value();

        assertThat(retake.getQuestions()).extracting(Question::comparableText).doesNotContain("question 1");
        assertThat(generator.requests()).hasSize(2);
    }

    @Test
    @DisplayName("US17 escenario 3: if the AI keeps repeating, the attempt is not created and the node is unchanged")
    void newAttempt_whenTheGeneratorKeepsRepeating_failsAndLeavesTheNodeUnchanged() {
        AssessmentBlueprint first = generate().value();
        generator.repeatPreviousQuestions(Integer.MAX_VALUE, AssessmentBlueprint.QUESTION_COUNT);

        Result<AssessmentBlueprint> result = generate();

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(LearningPathError.QUESTION_GENERATION_FAILED);
        assertThat(generator.requests()).hasSize(1 + AssessmentBlueprintCommandServiceImpl.MAX_GENERATION_ATTEMPTS);
        assertThat(blueprints.blueprints()).containsExactly(first);
        assertThat(path().getNode(1).orElseThrow().getAssessmentBlueprintId()).isEqualTo(first.getId());
        assertThat(path().getNode(1).orElseThrow().getStatus()).isEqualTo(NodeStatus.AVAILABLE);
    }

    @Test
    void newAttempt_whenTheWholeFirstAnswerRepeatsThePreviousAttempt_asksAgain() {
        // A previous attempt with exactly the questions the generator answers first.
        blueprints.save(new AssessmentBlueprint(1, "networking-basics", TestData.questions(5)));

        AssessmentBlueprint retake = generate().value();

        assertThat(texts(retake)).doesNotHaveDuplicates().doesNotContainAnyElementsOf(
                TestData.questions(5).stream().map(Question::getQuestionString).toList());
        assertThat(generator.requests()).hasSize(2);
    }

    // ---------- Optional rule: a linked certificate is required ----------

    @Test
    void whenACertificateIsRequired_aNodeWithoutOneReturnsCertificateRequired() {
        service = service(true);

        Result<AssessmentBlueprint> result = generate();

        assertThat(result.error()).isEqualTo(LearningPathError.CERTIFICATE_REQUIRED);
        assertThat(result.message()).isNotBlank().isNotEqualTo("CertificateRequired");
        assertThat(generator.requests()).isEmpty();
    }

    @Test
    @DisplayName("US15 escenario 1: the linked certificate enables the practical assessment of the node")
    void whenACertificateIsRequired_aNodeWithALinkedCertificateGetsItsAssessment() {
        service = service(true);
        path().linkCertificate(1, 40);

        assertThat(generate().isSuccess()).isTrue();
    }

    @Test
    void byDefault_aNodeWithoutCertificateGetsItsAssessment() {
        assertThat(generate().isSuccess()).isTrue();
    }
}
