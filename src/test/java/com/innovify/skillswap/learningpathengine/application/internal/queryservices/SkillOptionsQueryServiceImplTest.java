package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillCandidate;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.application.queryservices.SkillOption;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetSkillOptionsQuery;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.shared.application.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

/** US07: the skills a goal may refer to, with the data the student needs to choose. */
class SkillOptionsQueryServiceImplTest {

    private static final String GOAL = "I want to build REST APIs with JWT";

    /** Answers what a test puts in it and remembers the texts it was asked about. */
    private static final class ScriptedMatcher implements SkillTaxonomyMatcher {

        private List<SkillCandidate> candidates = List.of();
        private RuntimeException failure;
        private final List<String> asked = new ArrayList<>();

        @Override
        public List<String> match(String text) {
            return rank(text).stream().map(SkillCandidate::tag).toList();
        }

        @Override
        public List<SkillCandidate> rank(String text) {
            asked.add(text);
            if (failure != null) {
                throw failure;
            }
            return candidates;
        }
    }

    private final ScriptedMatcher matcher = new ScriptedMatcher();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private SkillOptionsQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        ResourceBundleMessageSource messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        messages.setFallbackToSystemLocale(false);
        LocaleContextHolder.setLocale(Locale.US);
        service = new SkillOptionsQueryServiceImpl(matcher, TestData.TAXONOMY,
                new DefaultSkillGapAnalyzer(TestData.TAXONOMY), paths, messages);
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    private static SkillCandidate candidate(String tag, double affinity) {
        return new SkillCandidate(tag, affinity);
    }

    private Result<List<SkillOption>> options(int studentId, String goal) {
        return service.handle(new GetSkillOptionsQuery(studentId, goal));
    }

    private Result<List<SkillOption>> options() {
        return options(1, GOAL);
    }

    /** The student demonstrated these skills on a path of the goal "authentication-jwt". */
    private void demonstrate(int studentId, String... skillTags) {
        LearningPath path = paths.save(TestData.newUnsavedPath(studentId, "authentication-jwt"));
        List<String> done = List.of(skillTags);
        path.getNodes().stream().filter(node -> done.contains(node.getSkillTag())).forEach(node -> node.complete());
    }

    private static void assertFailure(Result<?> result, LearningPathError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    // ---------- The options ----------

    @Test
    void options_listTheSkillsBestFitFirstWithWhatTheStudentNeedsToChoose() {
        matcher.candidates = List.of(candidate("rest-api-design", 0.6), candidate("authentication-jwt", 0.9));

        Result<List<SkillOption>> result = options();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).containsExactly(
                new SkillOption("authentication-jwt", "authentication-jwt", null, 5, 5, 0.9),
                new SkillOption("rest-api-design", "rest-api-design", null, 4, 4, 0.6));
    }

    @Test
    void options_withASingleCandidate_stillReturnsAList() {
        matcher.candidates = List.of(candidate("sql-fundamentals", 1.0));

        Result<List<SkillOption>> result = options();

        assertThat(result.value()).containsExactly(new SkillOption("sql-fundamentals", "sql-fundamentals", null, 1, 1, 1.0));
    }

    @Test
    void options_keepTheOrderOfTheMatcherWhenTheFitIsTheSame() {
        matcher.candidates = List.of(candidate("rest-api-design", 0.5), candidate("sql-fundamentals", 0.5),
                candidate("http-basics", 0.5));

        assertThat(options().value()).extracting(SkillOption::skillTag)
                .containsExactly("rest-api-design", "sql-fundamentals", "http-basics");
    }

    @Test
    void options_haveNoDescriptionWhileTheCatalogHasNone() {
        matcher.candidates = List.of(candidate("sql-fundamentals", 1.0));

        assertThat(options().value()).allSatisfy(option -> assertThat(option.description()).isNull());
    }

    @Test
    void options_offerAtMostFiveSkills() {
        matcher.candidates = List.of(candidate("authentication-jwt", 0.9), candidate("rest-api-design", 0.8),
                candidate("http-basics", 0.7), candidate("networking-basics", 0.6),
                candidate("programming-fundamentals", 0.5), candidate("sql-fundamentals", 0.4));

        assertThat(options().value()).extracting(SkillOption::skillTag).containsExactly("authentication-jwt",
                "rest-api-design", "http-basics", "networking-basics", "programming-fundamentals");
    }

    @Test
    void options_leaveOutTheSkillsBelowTheMinimumAffinity() {
        matcher.candidates = List.of(candidate("rest-api-design", 0.3), candidate("sql-fundamentals", 0.29));

        assertThat(options().value()).extracting(SkillOption::skillTag).containsExactly("rest-api-design");
    }

    @Test
    void options_leaveOutTheSkillsThatAreNotInTheCatalog() {
        matcher.candidates = List.of(candidate("quantum-api-design", 0.95), candidate("rest-api-design", 0.5));

        assertThat(options().value()).extracting(SkillOption::skillTag).containsExactly("rest-api-design");
    }

    @Test
    void options_askTheMatcherWithTheStrippedGoal() {
        matcher.candidates = List.of(candidate("sql-fundamentals", 1.0));

        options(1, "  " + GOAL + " \n");

        assertThat(matcher.asked).containsExactly(GOAL);
    }

    // ---------- What the student already demonstrated ----------

    @Test
    void nodeCount_dependsOnWhatTheStudentAlreadyDemonstrated() {
        demonstrate(1, "networking-basics", "programming-fundamentals");
        matcher.candidates = List.of(candidate("authentication-jwt", 0.9), candidate("rest-api-design", 0.6));

        assertThat(options().value()).extracting(SkillOption::nodeCount, SkillOption::estimatedWeeks)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3, 3), org.assertj.core.groups.Tuple.tuple(2, 2));
    }

    @Test
    void options_doNotUseWhatAnotherStudentDemonstrated() {
        demonstrate(2, "networking-basics", "programming-fundamentals");
        matcher.candidates = List.of(candidate("authentication-jwt", 0.9));

        assertThat(options(1, GOAL).value().get(0).nodeCount()).isEqualTo(5);
    }

    @Test
    void options_leaveOutASkillTheStudentAlreadyDemonstrated() {
        paths.save(TestData.newUnsavedPath(1, "sql-fundamentals")).getNodes().forEach(node -> node.complete());
        matcher.candidates = List.of(candidate("sql-fundamentals", 0.9), candidate("rest-api-design", 0.5));

        assertThat(options().value()).extracting(SkillOption::skillTag).containsExactly("rest-api-design");
    }

    @Test
    void options_whenTheStudentAlreadyDemonstratedEverySkill_failWithGoalAlreadyAchieved() {
        paths.save(TestData.newUnsavedPath(1, "sql-fundamentals")).getNodes().forEach(node -> node.complete());
        matcher.candidates = List.of(candidate("sql-fundamentals", 0.9));

        assertFailure(options(), LearningPathError.GOAL_ALREADY_ACHIEVED);
    }

    @Test
    void options_whenASkillIsLeftOut_theNextOnesTakeItsPlaceUpToTheMaximum() {
        paths.save(TestData.newUnsavedPath(1, "sql-fundamentals")).getNodes().forEach(node -> node.complete());
        matcher.candidates = List.of(candidate("sql-fundamentals", 0.99), candidate("authentication-jwt", 0.9),
                candidate("rest-api-design", 0.8), candidate("http-basics", 0.7), candidate("networking-basics", 0.6),
                candidate("programming-fundamentals", 0.5));

        assertThat(options().value()).hasSize(5).extracting(SkillOption::skillTag)
                .doesNotContain("sql-fundamentals");
    }

    // ---------- Rejections ----------

    @Test
    void options_whenNoSkillFits_failWithGoalNotInterpretable() {
        assertFailure(options(), LearningPathError.GOAL_NOT_INTERPRETABLE);
    }

    @Test
    void options_whenEveryCandidateIsBelowTheMinimum_failWithGoalNotInterpretable() {
        matcher.candidates = List.of(candidate("rest-api-design", 0.2));

        assertFailure(options(), LearningPathError.GOAL_NOT_INTERPRETABLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "\n"})
    void options_withoutAGoal_failWithInvalidGoalAndDoNotAskTheMatcher(String goal) {
        assertFailure(options(1, goal), LearningPathError.INVALID_GOAL);
        assertThat(matcher.asked).isEmpty();
    }

    @Test
    void options_withNullOrTooLongAGoal_failWithInvalidGoal() {
        assertFailure(options(1, null), LearningPathError.INVALID_GOAL);
        assertFailure(options(1, "a".repeat(501)), LearningPathError.INVALID_GOAL);
        assertThat(matcher.asked).isEmpty();
    }

    @Test
    void options_withAGoalOfTheMaximumLength_areAccepted() {
        matcher.candidates = List.of(candidate("sql-fundamentals", 1.0));

        assertThat(options(1, "a".repeat(500)).isSuccess()).isTrue();
    }

    @Test
    void options_whenSomethingFails_failWithInternalServerError() {
        matcher.failure = new IllegalStateException("boom");

        assertFailure(options(), LearningPathError.INTERNAL_SERVER_ERROR);
    }

    @Test
    void options_saveNothing() {
        matcher.candidates = List.of(candidate("sql-fundamentals", 1.0));

        options();

        assertThat(paths.paths()).isEmpty();
        assertThat(paths.saveCalls()).isZero();
    }
}
