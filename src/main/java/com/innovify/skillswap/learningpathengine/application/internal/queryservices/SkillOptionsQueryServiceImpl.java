package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import com.innovify.skillswap.learningpathengine.application.internal.commandservices.LearningPathFailures;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillCandidate;
import com.innovify.skillswap.learningpathengine.application.internal.outboundservices.SkillTaxonomyMatcher;
import com.innovify.skillswap.learningpathengine.application.queryservices.SkillOption;
import com.innovify.skillswap.learningpathengine.application.queryservices.SkillOptionsQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetSkillOptionsQuery;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.services.SkillGapAnalyzer;
import com.innovify.skillswap.learningpathengine.domain.services.SkillTaxonomy;
import com.innovify.skillswap.shared.application.Result;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

/**
 * Lists the skills a goal may refer to so the student can choose. It reads only: nothing is saved and the limits of
 * the plan are not checked (they apply when the path is declared).
 */
@Service
public class SkillOptionsQueryServiceImpl implements SkillOptionsQueryService {

    private static final Logger log = LoggerFactory.getLogger(SkillOptionsQueryServiceImpl.class);

    /** Most skills offered. */
    static final int MAX_OPTIONS = 5;

    /** A skill that fits the goal less than this is not offered. */
    static final double MIN_AFFINITY = 0.3;

    /** The estimate of the time to finish a path: one week per node. */
    static final int WEEKS_PER_NODE = 1;

    private final SkillTaxonomyMatcher taxonomyMatcher;
    private final SkillTaxonomy taxonomy;
    private final SkillGapAnalyzer skillGapAnalyzer;
    private final LearningPathRepository learningPathRepository;
    private final LearningPathFailures failures;

    public SkillOptionsQueryServiceImpl(SkillTaxonomyMatcher taxonomyMatcher, SkillTaxonomy taxonomy,
                                        SkillGapAnalyzer skillGapAnalyzer,
                                        LearningPathRepository learningPathRepository, MessageSource messageSource) {
        this.taxonomyMatcher = taxonomyMatcher;
        this.taxonomy = taxonomy;
        this.skillGapAnalyzer = skillGapAnalyzer;
        this.learningPathRepository = learningPathRepository;
        this.failures = new LearningPathFailures(messageSource);
    }

    @Override
    public Result<List<SkillOption>> handle(GetSkillOptionsQuery query) {
        String text = query.goal() == null ? "" : query.goal().strip();
        if (text.isEmpty() || text.length() > CareerGoal.MAX_RAW_TEXT_LENGTH) {
            return failures.failure(LearningPathError.INVALID_GOAL);
        }

        try {
            // Only skills of the catalog, whatever the matcher answers; the best fit first, and the catalog order
            // among the same fit.
            List<SkillCandidate> candidates = taxonomyMatcher.rank(text).stream()
                    .filter(candidate -> candidate.affinity() >= MIN_AFFINITY)
                    .filter(candidate -> taxonomy.contains(candidate.tag()))
                    .sorted(Comparator.comparingDouble(SkillCandidate::affinity).reversed())
                    .toList();
            if (candidates.isEmpty()) {
                return failures.failure(LearningPathError.GOAL_NOT_INTERPRETABLE);
            }

            Collection<String> demonstrated = learningPathRepository.findCompletedSkillTagsByStudentId(query.studentId());
            List<SkillOption> options = new ArrayList<>();
            for (SkillCandidate candidate : candidates) {
                SkillGap gap = skillGapAnalyzer.analyze(new CareerGoal(text, List.of(candidate.tag())), demonstrated);
                if (gap.isEmpty()) {
                    continue;
                }
                int nodes = gap.missingSkillTags().size();
                options.add(new SkillOption(candidate.tag(), taxonomy.nameOf(candidate.tag()), null, nodes,
                        nodes * WEEKS_PER_NODE, candidate.affinity()));
                if (options.size() == MAX_OPTIONS) {
                    break;
                }
            }
            if (options.isEmpty()) {
                return failures.failure(LearningPathError.GOAL_ALREADY_ACHIEVED);
            }
            return Result.success(List.copyOf(options));
        } catch (RuntimeException exception) {
            log.error("Could not list the skill options of student {}", query.studentId(), exception);
            return failures.failure(LearningPathFailures.toError(exception));
        }
    }
}
