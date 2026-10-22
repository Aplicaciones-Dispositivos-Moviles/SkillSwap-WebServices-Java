package com.innovify.skillswap.reputation.application.internal.queryservices;

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
import com.innovify.skillswap.reputation.application.queryservices.DemandMetrics;
import com.innovify.skillswap.reputation.application.queryservices.DifficultyMetrics;
import com.innovify.skillswap.reputation.application.queryservices.PlatformMetricsQueryService;
import com.innovify.skillswap.reputation.application.queryservices.VerifierMetrics;
import com.innovify.skillswap.reputation.domain.model.ReputationError;
import com.innovify.skillswap.reputation.domain.model.aggregates.VerifierReliability;
import com.innovify.skillswap.reputation.domain.model.queries.GetDemandMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetDifficultyMetricsQuery;
import com.innovify.skillswap.reputation.domain.model.queries.GetVerifierMetricsQuery;
import com.innovify.skillswap.reputation.domain.repositories.VerifierReliabilityRepository;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;

/**
 * Platform metrics query service. Only a Verificador senior can ask. The data lives in other contexts, so each metric
 * is requested through their facades; this service only resolves the period, names the skills and joins the verifiers.
 */
public class PlatformMetricsQueryServiceImpl implements PlatformMetricsQueryService {

    /** Days of the default period: the last 30 days, today included. */
    public static final int DEFAULT_PERIOD_DAYS = 30;
    public static final int MAX_PERIOD_DAYS = 366;
    public static final int DEFAULT_LIMIT = 10;
    public static final int MAX_LIMIT = 50;
    /** Attempts a skill needs in the period to be listed as difficult. */
    public static final int MIN_ATTEMPTS_FOR_DIFFICULTY = 5;

    private static final Logger log = LoggerFactory.getLogger(PlatformMetricsQueryServiceImpl.class);

    private final VerifierReliabilityRepository reliabilityRepository;
    private final LearningPathContextFacade learningPathFacade;
    private final SkillCatalogContextFacade skillCatalogFacade;
    private final CredentialContextFacade credentialFacade;
    private final AssessmentMetricsContextFacade assessmentFacade;
    private final IamContextFacade iamFacade;
    private final MessageSource messageSource;
    private final Clock clock;

    public PlatformMetricsQueryServiceImpl(VerifierReliabilityRepository reliabilityRepository,
                                           LearningPathContextFacade learningPathFacade,
                                           SkillCatalogContextFacade skillCatalogFacade,
                                           CredentialContextFacade credentialFacade,
                                           AssessmentMetricsContextFacade assessmentFacade,
                                           IamContextFacade iamFacade, MessageSource messageSource, Clock clock) {
        this.reliabilityRepository = reliabilityRepository;
        this.learningPathFacade = learningPathFacade;
        this.skillCatalogFacade = skillCatalogFacade;
        this.credentialFacade = credentialFacade;
        this.assessmentFacade = assessmentFacade;
        this.iamFacade = iamFacade;
        this.messageSource = messageSource;
        this.clock = clock;
    }

    @Override
    public Result<DemandMetrics> handle(GetDemandMetricsQuery query) {
        try {
            if (!isSenior(query.requesterUserId())) {
                return failure(ReputationError.NOT_SENIOR_VERIFIER);
            }
            Optional<Period> period = resolvePeriod(query.from(), query.to());
            if (period.isEmpty()) {
                return failure(ReputationError.INVALID_PERIOD);
            }
            Optional<Integer> limit = resolveLimit(query.limit());
            if (limit.isEmpty()) {
                return failure(ReputationError.INVALID_LIMIT);
            }

            List<SkillDemandView> skills = learningPathFacade.getMostRequestedSkills(period.get().fromInstant(),
                    period.get().toInstant(), limit.get());
            List<CertificationDemandView> certifications = credentialFacade.getMostFrequentCertifications(
                    period.get().fromInstant(), period.get().toInstant(), limit.get());
            Map<String, String> names = skillCatalogFacade.getSkillNames(
                    skills.stream().map(SkillDemandView::skillTag).toList());

            return Result.success(new DemandMetrics(period.get().from(), period.get().to(),
                    skills.stream()
                            .map(skill -> new DemandMetrics.SkillDemand(skill.skillTag(),
                                    names.getOrDefault(skill.skillTag(), skill.skillTag()), skill.pathCount()))
                            .toList(),
                    certifications.stream()
                            .map(certification -> new DemandMetrics.CertificationDemand(certification.courseName(),
                                    certification.count()))
                            .toList()));
        } catch (RuntimeException exception) {
            log.error("Could not calculate the demand metrics", exception);
            return failure(toError(exception));
        }
    }

    @Override
    public Result<DifficultyMetrics> handle(GetDifficultyMetricsQuery query) {
        try {
            if (!isSenior(query.requesterUserId())) {
                return failure(ReputationError.NOT_SENIOR_VERIFIER);
            }
            Optional<Period> period = resolvePeriod(query.from(), query.to());
            if (period.isEmpty()) {
                return failure(ReputationError.INVALID_PERIOD);
            }
            Optional<Integer> limit = resolveLimit(query.limit());
            if (limit.isEmpty()) {
                return failure(ReputationError.INVALID_LIMIT);
            }

            List<SkillDifficultyView> skills = assessmentFacade.getSkillDifficulty(period.get().fromInstant(),
                    period.get().toInstant(), MIN_ATTEMPTS_FOR_DIFFICULTY, limit.get());
            List<SubTopicDifficultyView> subTopics = assessmentFacade.getSubTopicDifficulty(
                    period.get().fromInstant(), period.get().toInstant(), limit.get());
            Set<String> tags = new LinkedHashSet<>();
            skills.forEach(skill -> tags.add(skill.skillTag()));
            subTopics.forEach(subTopic -> tags.add(subTopic.skillTag()));
            Map<String, String> names = skillCatalogFacade.getSkillNames(new ArrayList<>(tags));

            return Result.success(new DifficultyMetrics(period.get().from(), period.get().to(),
                    MIN_ATTEMPTS_FOR_DIFFICULTY,
                    skills.stream()
                            .map(skill -> new DifficultyMetrics.SkillDifficulty(skill.skillTag(),
                                    names.getOrDefault(skill.skillTag(), skill.skillTag()), skill.attempts(),
                                    skill.failed(), skill.failureRate()))
                            .toList(),
                    subTopics.stream()
                            .map(subTopic -> new DifficultyMetrics.SubTopicDifficulty(subTopic.skillTag(),
                                    names.getOrDefault(subTopic.skillTag(), subTopic.skillTag()),
                                    subTopic.subTopic(), subTopic.weakCount(), subTopic.shareOfFailures()))
                            .toList()));
        } catch (RuntimeException exception) {
            log.error("Could not calculate the difficulty metrics", exception);
            return failure(toError(exception));
        }
    }

    @Override
    public Result<VerifierMetrics> handle(GetVerifierMetricsQuery query) {
        try {
            if (!isSenior(query.requesterUserId())) {
                return failure(ReputationError.NOT_SENIOR_VERIFIER);
            }
            String order = query.order() == null || query.order().isBlank()
                    ? "desc" : query.order().strip().toLowerCase(Locale.ROOT);
            if (!order.equals("asc") && !order.equals("desc")) {
                return failure(ReputationError.INVALID_ORDER);
            }

            Map<Integer, VerifierActivityView> activity = assessmentFacade.getVerifierActivity().stream()
                    .collect(Collectors.toMap(VerifierActivityView::verifierUserId, Function.identity(),
                            (first, second) -> first));
            Comparator<VerifierReliability> byScore = Comparator.comparingInt(
                    (VerifierReliability reliability) -> reliability.getScore().value());
            Comparator<VerifierReliability> comparator = (order.equals("asc") ? byScore : byScore.reversed())
                    .thenComparingInt(VerifierReliability::getVerifierUserId);

            List<VerifierMetrics.VerifierMetric> verifiers = reliabilityRepository.findAll().stream()
                    .sorted(comparator)
                    .map(reliability -> {
                        VerifierActivityView verifierActivity = activity.get(reliability.getVerifierUserId());
                        return new VerifierMetrics.VerifierMetric(reliability.getVerifierUserId(),
                                iamFacade.getRegisteredFullName(reliability.getVerifierUserId()).orElse(null),
                                reliability.getScore().value(), reliability.getRank().value(),
                                reliability.getResolvedCasesCount(), reliability.getOverturnedDecisionsCount(),
                                reliability.getMissedDeadlinesCount(), reliability.isSeniorVerifier(),
                                verifierActivity != null && verifierActivity.suspended(),
                                verifierActivity == null ? null : verifierActivity.averageResolutionHours());
                    })
                    .toList();
            return Result.success(new VerifierMetrics(verifiers));
        } catch (RuntimeException exception) {
            log.error("Could not calculate the verifier metrics", exception);
            return failure(toError(exception));
        }
    }

    private boolean isSenior(int userId) {
        return reliabilityRepository.findByVerifierUserId(userId)
                .map(VerifierReliability::isSeniorVerifier)
                .orElse(false);
    }

    /** The period asked for, or the last 30 days. Empty when a date is not yyyy-MM-dd, is reversed or too long. */
    private Optional<Period> resolvePeriod(String fromText, String toText) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
            LocalDate to = toText == null || toText.isBlank() ? null : LocalDate.parse(toText.strip());
            LocalDate from = fromText == null || fromText.isBlank() ? null : LocalDate.parse(fromText.strip());
            if (to == null) {
                to = from != null && from.isAfter(today) ? from : today;
            }
            if (from == null) {
                from = to.minusDays(DEFAULT_PERIOD_DAYS - 1L);
            }
            if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) + 1 > MAX_PERIOD_DAYS) {
                return Optional.empty();
            }
            return Optional.of(new Period(from, to,
                    from.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> resolveLimit(Integer limit) {
        if (limit == null) {
            return Optional.of(DEFAULT_LIMIT);
        }
        return limit >= 1 && limit <= MAX_LIMIT ? Optional.of(limit) : Optional.empty();
    }

    private <T> Result<T> failure(ReputationError error) {
        String code = ErrorCodes.of(error);
        return Result.failure(error, messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
    }

    private static ReputationError toError(RuntimeException exception) {
        return exception instanceof DataAccessException
                ? ReputationError.DATABASE_ERROR
                : ReputationError.INTERNAL_SERVER_ERROR;
    }

    /** The days asked for and the instants that bound them: the start of the first day until the end of the last. */
    private record Period(LocalDate from, LocalDate to, Instant fromInstant, Instant toInstant) {
    }
}
