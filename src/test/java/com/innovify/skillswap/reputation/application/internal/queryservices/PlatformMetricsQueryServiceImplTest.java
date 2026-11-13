package com.innovify.skillswap.reputation.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.acl.AssessmentMetricsContextFacade;
import com.innovify.skillswap.assessmentpeerreview.application.acl.SkillDifficultyView;
import com.innovify.skillswap.assessmentpeerreview.application.acl.SubTopicDifficultyView;
import com.innovify.skillswap.assessmentpeerreview.application.acl.VerifierActivityView;
import com.innovify.skillswap.credentialverification.application.acl.CertificationDemandView;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.SkillCatalogContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.SkillDemandView;
import com.innovify.skillswap.reputation.application.fakes.FakeVerifierReliabilityRepository;
import com.innovify.skillswap.reputation.application.fakes.TestMessages;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.queries.GetDemandMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetDifficultyMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierMetricsQuery;
import com.innovify.skillswap.reputation.domain.services.DefaultVerifierReliabilityCalculator;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class PlatformMetricsQueryServiceImplTest {

    private static final int SENIOR = 1;
    private static final int JUNIOR = 2;
    private static final Instant NOW = Instant.parse("2026-10-15T10:00:00Z");

    private final FakeVerifierReliabilityRepository reliabilities = new FakeVerifierReliabilityRepository();
    private final List<Instant[]> demandRanges = new ArrayList<>();
    private List<SkillDemandView> skillDemand = List.of();
    private List<CertificationDemandView> certificationDemand = List.of();
    private List<SkillDifficultyView> skillDifficulty = List.of();
    private List<SubTopicDifficultyView> subTopicDifficulty = List.of();
    private List<VerifierActivityView> activity = List.of();
    private final List<Integer> limits = new ArrayList<>();
    private int minAttemptsAsked;
    private RuntimeException failure;
    private PlatformMetricsQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        reliabilities.save(senior(SENIOR));
        VerifierReliability junior = new VerifierReliability(JUNIOR);
        junior.recordOverturn(new DefaultVerifierReliabilityCalculator());
        reliabilities.save(junior);

        LearningPathContextFacade learningPaths = proxy(LearningPathContextFacade.class, (method, args) -> {
            if (failure != null) {
                throw failure;
            }
            demandRanges.add(new Instant[] {(Instant) args[0], (Instant) args[1]});
            limits.add((Integer) args[2]);
            return skillDemand;
        });
        SkillCatalogContextFacade catalog = proxy(SkillCatalogContextFacade.class, (method, args) -> {
            Map<String, String> names = Map.of("java-basics", "Java Basics", "sql-basics", "SQL Basics");
            Map<String, String> found = new java.util.HashMap<>();
            for (Object tag : (Collection<?>) args[0]) {
                if (names.containsKey(tag)) {
                    found.put((String) tag, names.get(tag));
                }
            }
            return found;
        });
        CredentialContextFacade credentials = proxy(CredentialContextFacade.class, (method, args) -> {
            limits.add((Integer) args[2]);
            return certificationDemand;
        });
        AssessmentMetricsContextFacade assessments = proxy(AssessmentMetricsContextFacade.class, (method, args) -> {
            if (failure != null) {
                throw failure;
            }
            return switch (method) {
                case "getSkillDifficulty" -> {
                    demandRanges.add(new Instant[] {(Instant) args[0], (Instant) args[1]});
                    minAttemptsAsked = (Integer) args[2];
                    limits.add((Integer) args[3]);
                    yield skillDifficulty;
                }
                case "getSubTopicDifficulty" -> subTopicDifficulty;
                case "getVerifierActivity" -> activity;
                default -> throw new UnsupportedOperationException(method);
            };
        });
        IamContextFacade iam = proxy(IamContextFacade.class, (method, args) ->
                (Integer) args[0] == SENIOR ? Optional.of("Sofia Senior") : Optional.empty());

        service = new PlatformMetricsQueryServiceImpl(reliabilities, learningPaths, catalog, credentials,
                assessments, iam, TestMessages.source(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static VerifierReliability senior(int userId) {
        VerifierReliability reliability = new VerifierReliability(userId);
        var calculator = new DefaultVerifierReliabilityCalculator();
        for (int i = 0; i < 100; i++) {
            reliability.recordResolution(calculator);
        }
        return reliability;
    }

    private interface Handler {
        Object handle(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> handler.handle(method.getName(), args == null ? new Object[0] : args));
    }

    // ---------- Access ----------

    @Test
    void everyMetric_aNonSeniorVerifierOrAStranger_isForbidden() {
        assertThat(service.handle(new GetDemandMetricsQuery(JUNIOR, null, null, null)).error())
                .isEqualTo(ReputationError.NOT_SENIOR_VERIFIER);
        assertThat(service.handle(new GetDifficultyMetricsQuery(99, null, null, null)).error())
                .isEqualTo(ReputationError.NOT_SENIOR_VERIFIER);
        assertThat(service.handle(new GetVerifierMetricsQuery(JUNIOR, null)).error())
                .isEqualTo(ReputationError.NOT_SENIOR_VERIFIER);
        assertThat(demandRanges).isEmpty();
    }

    // ---------- Demand ----------

    @Test
    void demand_withoutDates_usesTheLast30DaysUpToTheEndOfToday() {
        skillDemand = List.of(new SkillDemandView("java-basics", 7), new SkillDemandView("unknown-tag", 2));
        certificationDemand = List.of(new CertificationDemandView("AWS Cloud Practitioner", 4));

        var result = service.handle(new GetDemandMetricsQuery(SENIOR, null, null, null));

        assertThat(result.isSuccess()).isTrue();
        var metrics = result.value();
        assertThat(metrics.from()).isEqualTo(LocalDate.of(2026, 9, 16));
        assertThat(metrics.to()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(demandRanges.get(0)[0]).isEqualTo(Instant.parse("2026-09-16T00:00:00Z"));
        assertThat(demandRanges.get(0)[1]).isEqualTo(Instant.parse("2026-10-16T00:00:00Z"));
        assertThat(limits).containsOnly(PlatformMetricsQueryServiceImpl.DEFAULT_LIMIT);
        assertThat(metrics.topSkills()).extracting("skillTag", "skillName", "pathCount")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("java-basics", "Java Basics", 7),
                        org.assertj.core.groups.Tuple.tuple("unknown-tag", "unknown-tag", 2));
        assertThat(metrics.topCertifications()).extracting("courseName", "count")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("AWS Cloud Practitioner", 4));
    }

    @Test
    void demand_withDates_includesTheLastDay() {
        var result = service.handle(new GetDemandMetricsQuery(SENIOR, "2026-10-01", "2026-10-03", 5));

        assertThat(result.isSuccess()).isTrue();
        assertThat(demandRanges.get(0)[0]).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(demandRanges.get(0)[1]).isEqualTo(Instant.parse("2026-10-04T00:00:00Z"));
        assertThat(limits).containsOnly(5);
    }

    @Test
    void demand_onlyFrom_endsToday() {
        var result = service.handle(new GetDemandMetricsQuery(SENIOR, "2026-10-01", null, null));

        assertThat(result.value().from()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(result.value().to()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void demand_badPeriods_areInvalid() {
        for (String[] period : new String[][] {{"15/10/2026", null}, {"2026-10-05", "2026-10-01"},
                {"2025-01-01", "2026-10-01"}, {null, "nope"}}) {
            var result = service.handle(new GetDemandMetricsQuery(SENIOR, period[0], period[1], null));
            assertThat(result.error()).as(period[0] + " " + period[1]).isEqualTo(ReputationError.INVALID_PERIOD);
        }
        var oneYear = service.handle(new GetDemandMetricsQuery(SENIOR, "2025-10-01", "2026-09-30", null));
        assertThat(oneYear.isSuccess()).isTrue();
    }

    @Test
    void demand_limitOutOfRange_isInvalid() {
        assertThat(service.handle(new GetDemandMetricsQuery(SENIOR, null, null, 0)).error())
                .isEqualTo(ReputationError.INVALID_LIMIT);
        assertThat(service.handle(new GetDemandMetricsQuery(SENIOR, null, null, 51)).error())
                .isEqualTo(ReputationError.INVALID_LIMIT);
        assertThat(service.handle(new GetDemandMetricsQuery(SENIOR, null, null, 50)).isSuccess()).isTrue();
    }

    @Test
    void demand_failureOfTheDatabase_returnsDatabaseError() {
        failure = new DataAccessResourceFailureException("down");

        assertThat(service.handle(new GetDemandMetricsQuery(SENIOR, null, null, null)).error())
                .isEqualTo(ReputationError.DATABASE_ERROR);

        failure = new IllegalStateException("boom");
        assertThat(service.handle(new GetDemandMetricsQuery(SENIOR, null, null, null)).error())
                .isEqualTo(ReputationError.INTERNAL_SERVER_ERROR);
    }

    // ---------- Difficulty ----------

    @Test
    void difficulty_namesTheSkillsAndAsksFor5AttemptsAtLeast() {
        skillDifficulty = List.of(new SkillDifficultyView("sql-basics", 10, 6, 0.6));
        subTopicDifficulty = List.of(new SubTopicDifficultyView("sql-basics", "joins", 4, 0.67),
                new SubTopicDifficultyView("java-basics", "streams", 1, 0.5));

        var result = service.handle(new GetDifficultyMetricsQuery(SENIOR, null, null, 3));

        assertThat(result.isSuccess()).isTrue();
        assertThat(minAttemptsAsked).isEqualTo(5);
        assertThat(result.value().minAttempts()).isEqualTo(5);
        assertThat(result.value().skills()).extracting("skillName", "attempts", "failed", "failureRate")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SQL Basics", 10, 6, 0.6));
        assertThat(result.value().subTopics()).extracting("skillName", "subTopic", "weakCount")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("SQL Basics", "joins", 4),
                        org.assertj.core.groups.Tuple.tuple("Java Basics", "streams", 1));
    }

    @Test
    void difficulty_invalidInputs_areRejected() {
        assertThat(service.handle(new GetDifficultyMetricsQuery(SENIOR, "x", null, null)).error())
                .isEqualTo(ReputationError.INVALID_PERIOD);
        assertThat(service.handle(new GetDifficultyMetricsQuery(SENIOR, null, null, 100)).error())
                .isEqualTo(ReputationError.INVALID_LIMIT);
    }

    @Test
    void difficulty_failure_isReported() {
        failure = new DataAccessResourceFailureException("down");

        assertThat(service.handle(new GetDifficultyMetricsQuery(SENIOR, null, null, null)).error())
                .isEqualTo(ReputationError.DATABASE_ERROR);
    }

    // ---------- Verifiers ----------

    @Test
    void verifiers_areOrderedByScoreHighestFirstByDefault() {
        activity = List.of(new VerifierActivityView(SENIOR, false, 3.5), new VerifierActivityView(JUNIOR, true, null));

        var result = service.handle(new GetVerifierMetricsQuery(SENIOR, null));

        assertThat(result.isSuccess()).isTrue();
        var verifiers = result.value().verifiers();
        assertThat(verifiers).extracting("verifierUserId").containsExactly(SENIOR, JUNIOR);
        assertThat(verifiers.get(0).fullName()).isEqualTo("Sofia Senior");
        assertThat(verifiers.get(0).rank()).isEqualTo("Gold");
        assertThat(verifiers.get(0).seniorVerifier()).isTrue();
        assertThat(verifiers.get(0).suspended()).isFalse();
        assertThat(verifiers.get(0).averageResolutionHours()).isEqualTo(3.5);
        assertThat(verifiers.get(1).fullName()).isNull();
        assertThat(verifiers.get(1).suspended()).isTrue();
        assertThat(verifiers.get(1).averageResolutionHours()).isNull();
    }

    @Test
    void verifiers_ascOrderReversesAndIsCaseInsensitive() {
        var result = service.handle(new GetVerifierMetricsQuery(SENIOR, " ASC "));

        assertThat(result.value().verifiers()).extracting("verifierUserId").containsExactly(JUNIOR, SENIOR);
    }

    @Test
    void verifiers_aVerifierWithoutProfileIsNotSuspendedAndHasNoAverage() {
        var result = service.handle(new GetVerifierMetricsQuery(SENIOR, "desc"));

        assertThat(result.value().verifiers()).allSatisfy(v -> {
            assertThat(v.suspended()).isFalse();
            assertThat(v.averageResolutionHours()).isNull();
        });
    }

    @Test
    void verifiers_unknownOrder_isInvalid() {
        assertThat(service.handle(new GetVerifierMetricsQuery(SENIOR, "up")).error())
                .isEqualTo(ReputationError.INVALID_ORDER);
    }

    @Test
    void verifiers_failure_isReported() {
        failure = new DataAccessResourceFailureException("down");

        assertThat(service.handle(new GetVerifierMetricsQuery(SENIOR, null)).error())
                .isEqualTo(ReputationError.DATABASE_ERROR);
    }
}
