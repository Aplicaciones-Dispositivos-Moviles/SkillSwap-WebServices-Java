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
import com.innovify.skillswap.shared.application.Result;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Node ids of the seeded path (student 1): 1 networking-basics, 2 programming-fundamentals (available),
 * 3 http-basics, 4 rest-api-design, 5 authentication-jwt (locked).
 */
class AssessmentBlueprintCommandServiceImplTest {

    private final FakeAssessmentBlueprintRepository blueprints = new FakeAssessmentBlueprintRepository();
    private final FakeQuestionGenerationService generator = new FakeQuestionGenerationService();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private final AtomicInteger transactions = new AtomicInteger();
    private AssessmentBlueprintCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        TransactionOperations counting = new TransactionOperations() {
            @Override
            public <T> T execute(org.springframework.transaction.support.TransactionCallback<T> action) {
                transactions.incrementAndGet();
                return TransactionOperations.withoutTransaction().execute(action);
            }
        };

        LocaleContextHolder.setLocale(Locale.US);
        service = new AssessmentBlueprintCommandServiceImpl(paths, blueprints, generator, counting, messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private LearningPath seedPath() {
        return paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
    }

    private Result<AssessmentBlueprint> generate(int nodeId, int studentId) {
        return service.handle(new GenerateAssessmentBlueprintCommand(nodeId, studentId));
    }

    private Result<AssessmentBlueprint> generate(int nodeId) {
        return generate(nodeId, 1);
    }

    private static void assertFailure(Result<?> result, LearningPathError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    // ---------- Happy path ----------

    @Test
    void generate_forAnAvailableNode_createsTheBlueprintAndPointsTheNodeToIt() {
        LearningPath path = seedPath();
        int savesBefore = paths.saveCalls();

        Result<AssessmentBlueprint> result = generate(1);

        assertThat(result.isSuccess()).isTrue();
        AssessmentBlueprint blueprint = result.value();
        assertThat(blueprint.getPathNodeId()).isEqualTo(1);
        assertThat(blueprint.getSkillTag()).isEqualTo("networking-basics");
        assertThat(blueprint.getQuestions()).hasSize(AssessmentBlueprint.QUESTION_COUNT);
        assertThat(generator.requests()).containsExactly("networking-basics");
        assertThat(path.getNode(1).orElseThrow().getAssessmentBlueprintId()).isEqualTo(blueprint.getId());
        assertThat(paths.saveCalls()).isEqualTo(savesBefore + 1);
        assertThat(transactions).hasValue(1);
    }

    @Test
    void generate_again_keepsTheOldBlueprintAndPointsTheNodeToTheNewOne() {
        LearningPath path = seedPath();
        AssessmentBlueprint first = generate(1).value();

        AssessmentBlueprint second = generate(1).value();

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(blueprints.blueprints()).hasSize(2);
        assertThat(path.getNode(1).orElseThrow().getAssessmentBlueprintId()).isEqualTo(second.getId());
        assertThat(second.getQuestions()).extracting(Question::getQuestionString)
                .isNotEqualTo(first.getQuestions().stream().map(Question::getQuestionString).toList());
    }

    // ---------- Rejections ----------

    @Test
    void generate_forALockedNode_returnsNodeLockedListingThePendingPrerequisites() {
        seedPath();

        Result<AssessmentBlueprint> result = generate(4);

        assertFailure(result, LearningPathError.NODE_LOCKED);
        assertThat(result.details().get("pendingPrerequisites")).asList()
                .containsExactlyInAnyOrder("http-basics", "programming-fundamentals");
        assertThat(generator.requests()).isEmpty();
    }

    @Test
    void generate_forACompletedNode_returnsNodeAlreadyCompleted() {
        LearningPath path = seedPath();
        path.completeNode(1);

        assertFailure(generate(1), LearningPathError.NODE_ALREADY_COMPLETED);
        assertThat(generator.requests()).isEmpty();
    }

    @Test
    void generate_forAnotherStudentsNode_returnsNotPathOwnerWithoutGenerating() {
        seedPath();

        assertFailure(generate(1, 2), LearningPathError.NOT_PATH_OWNER);
        assertThat(generator.requests()).isEmpty();
        assertThat(blueprints.blueprints()).isEmpty();
    }

    @Test
    void generate_forAnUnknownNode_returnsNodeNotFound() {
        assertFailure(generate(99), LearningPathError.NODE_NOT_FOUND);
    }

    // ---------- Generator failures ----------

    @Test
    void generate_whenTheProviderFails_returnsQuestionGenerationFailedAndSavesNothing() {
        seedPath();
        int savesBefore = paths.saveCalls();
        generator.failWith(new IllegalStateException("provider unavailable"));

        assertFailure(generate(1), LearningPathError.QUESTION_GENERATION_FAILED);
        assertThat(blueprints.blueprints()).isEmpty();
        assertThat(paths.saveCalls()).isEqualTo(savesBefore);
        assertThat(transactions).hasValue(0);
    }

    @Test
    void generate_whenTheProviderReturnsTheWrongNumberOfQuestions_returnsQuestionGenerationFailed() {
        seedPath();
        generator.returnQuestionCount(4);

        assertFailure(generate(1), LearningPathError.QUESTION_GENERATION_FAILED);
        assertThat(blueprints.blueprints()).isEmpty();
    }

    // ---------- Persistence failures ----------

    @Test
    void generate_whenSavingTheBlueprintFails_returnsDatabaseError() {
        seedPath();
        blueprints.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(generate(1), LearningPathError.DATABASE_ERROR);
    }

    @Test
    void generate_whenSavingThePathFails_returnsDatabaseError() {
        seedPath();
        paths.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(generate(1), LearningPathError.DATABASE_ERROR);
    }

    @Test
    void generate_whenSomethingUnexpectedFails_returnsInternalServerError() {
        seedPath();
        blueprints.failOnSave(new IllegalStateException("boom"));

        assertFailure(generate(1), LearningPathError.INTERNAL_SERVER_ERROR);
    }
}
