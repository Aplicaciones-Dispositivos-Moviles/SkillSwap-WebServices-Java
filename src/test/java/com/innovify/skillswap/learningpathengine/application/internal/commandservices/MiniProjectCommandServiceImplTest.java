package com.innovify.skillswap.learningpathengine.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.commandservices.MiniProjectGeneration;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectGenerationService;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectRepository;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.commands.GenerateMiniProjectCommand;
import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.MiniProjectDraft;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Node ids of the seeded path (student 1): 1 networking-basics, 2 programming-fundamentals (available),
 * 3 http-basics, 4 rest-api-design, 5 authentication-jwt (locked).
 */
class MiniProjectCommandServiceImplTest {

    private final FakeMiniProjectRepository miniProjects = new FakeMiniProjectRepository();
    private final FakeMiniProjectGenerationService generator = new FakeMiniProjectGenerationService();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private MiniProjectCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);

        LocaleContextHolder.setLocale(Locale.US);
        service = new MiniProjectCommandServiceImpl(paths, miniProjects, generator, messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private LearningPath seedPath() {
        return paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
    }

    private Result<MiniProjectGeneration> generate(int nodeId, int studentId, boolean regenerate) {
        return service.handle(new GenerateMiniProjectCommand(nodeId, studentId, regenerate));
    }

    private Result<MiniProjectGeneration> generate(int nodeId) {
        return generate(nodeId, 1, false);
    }

    private static void assertFailure(Result<?> result, LearningPathError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    // ---------- Happy path ----------

    @Test
    void generate_forAnAvailableNode_createsAndSavesTheMiniProject() {
        seedPath();

        Result<MiniProjectGeneration> result = generate(1);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().created()).isTrue();
        MiniProject miniProject = result.value().miniProject();
        assertThat(miniProject.getId()).isNotNull();
        assertThat(miniProject.getPathNodeId()).isEqualTo(1);
        assertThat(miniProject.getSkillTag()).isEqualTo("networking-basics");
        assertThat(miniProject.getRubric()).hasSize(4);
        assertThat(Duration.between(miniProject.getCreatedAt(), miniProject.getDueAt())).isEqualTo(Duration.ofDays(5));
        assertThat(generator.requests()).containsExactly("networking-basics");
        assertThat(miniProjects.miniProjects()).containsExactly(miniProject);
    }

    @Test
    void generate_again_whileTheLatestCanBeDelivered_returnsTheSameWithoutGenerating() {
        seedPath();
        MiniProject first = generate(1).value().miniProject();

        Result<MiniProjectGeneration> second = generate(1);

        assertThat(second.isSuccess()).isTrue();
        assertThat(second.value().created()).isFalse();
        assertThat(second.value().miniProject()).isSameAs(first);
        assertThat(generator.requests()).hasSize(1);
        assertThat(miniProjects.miniProjects()).hasSize(1);
    }

    @Test
    void generate_askingToRegenerate_keepsTheOldOneAsHistory() {
        seedPath();
        MiniProject first = generate(1).value().miniProject();

        Result<MiniProjectGeneration> second = generate(1, 1, true);

        assertThat(second.value().created()).isTrue();
        assertThat(second.value().miniProject().getId()).isNotEqualTo(first.getId());
        assertThat(miniProjects.miniProjects()).hasSize(2);
        assertThat(miniProjects.findLatestByPathNodeId(1).orElseThrow()).isSameAs(second.value().miniProject());
    }

    @Test
    void generate_whenTheLatestExpired_generatesANewOne() {
        seedPath();
        MiniProject first = generate(1).value().miniProject();
        ReflectionTestUtils.setField(first, "dueAt", Instant.now().minusSeconds(1));

        Result<MiniProjectGeneration> second = generate(1);

        assertThat(second.value().created()).isTrue();
        assertThat(second.value().miniProject().getId()).isNotEqualTo(first.getId());
        assertThat(generator.requests()).hasSize(2);
    }

    // ---------- Rejections ----------

    @Test
    void generate_forALockedNode_returnsNodeLockedListingThePendingPrerequisites() {
        seedPath();

        Result<MiniProjectGeneration> result = generate(4);

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
    void generate_forAPausedPath_returnsPathPaused() {
        LearningPath path = seedPath();
        path.pause();

        assertFailure(generate(1), LearningPathError.PATH_PAUSED);
        assertThat(generator.requests()).isEmpty();
    }

    @Test
    void generate_forAnotherStudentsNode_returnsNotPathOwnerWithoutGenerating() {
        seedPath();

        assertFailure(generate(1, 2, false), LearningPathError.NOT_PATH_OWNER);
        assertThat(generator.requests()).isEmpty();
        assertThat(miniProjects.miniProjects()).isEmpty();
    }

    @Test
    void generate_forAnUnknownNode_returnsNodeNotFound() {
        assertFailure(generate(99), LearningPathError.NODE_NOT_FOUND);
    }

    // ---------- Generator failures ----------

    @Test
    void generate_whenTheProviderFails_returnsMiniProjectGenerationFailedAndSavesNothing() {
        seedPath();
        generator.failWith(new IllegalStateException("Gemini is down"));

        assertFailure(generate(1), LearningPathError.MINI_PROJECT_GENERATION_FAILED);
        assertThat(miniProjects.miniProjects()).isEmpty();
    }

    @Test
    void generate_whenTheProviderReturnsARubricThatBreaksTheContract_returnsMiniProjectGenerationFailed() {
        seedPath();
        List<RubricCriterion> wrongWeights = List.of(
                new RubricCriterion("A", "a", 50), new RubricCriterion("B", "b", 50),
                new RubricCriterion("C", "c", 50), new RubricCriterion("D", "d", 50));
        generator.returnDraft(new MiniProjectDraft("Title", "Statement.", wrongWeights));

        assertFailure(generate(1), LearningPathError.MINI_PROJECT_GENERATION_FAILED);
        assertThat(miniProjects.miniProjects()).isEmpty();
    }

    @Test
    void generate_whenTheProviderReturnsNothing_returnsMiniProjectGenerationFailed() {
        seedPath();
        generator.returnDraft(new MiniProjectDraft(null, null, null));

        assertFailure(generate(1), LearningPathError.MINI_PROJECT_GENERATION_FAILED);
    }

    @Test
    void generate_whenPersistenceFails_returnsDatabaseError() {
        seedPath();
        miniProjects.failOnSave(new DataIntegrityViolationException("failure"));

        assertFailure(generate(1), LearningPathError.DATABASE_ERROR);
    }
}
