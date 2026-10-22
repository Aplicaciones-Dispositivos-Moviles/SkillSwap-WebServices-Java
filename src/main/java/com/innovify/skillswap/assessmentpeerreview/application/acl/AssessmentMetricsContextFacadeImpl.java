package com.innovify.skillswap.assessmentpeerreview.application.acl;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssessmentMetricsContextFacadeImpl implements AssessmentMetricsContextFacade {

    private static final double SECONDS_PER_HOUR = 3600.0;

    private final AssessmentAttemptRepository attemptRepository;
    private final VerifierProfileRepository profileRepository;
    private final VerificationCaseRepository caseRepository;

    public AssessmentMetricsContextFacadeImpl(AssessmentAttemptRepository attemptRepository,
                                              VerifierProfileRepository profileRepository,
                                              VerificationCaseRepository caseRepository) {
        this.attemptRepository = attemptRepository;
        this.profileRepository = profileRepository;
        this.caseRepository = caseRepository;
    }

    @Override
    public List<SkillDifficultyView> getSkillDifficulty(Instant from, Instant to, int minAttempts, int limit) {
        if (!isValid(from, to, limit)) {
            return List.of();
        }

        // Per skill: [attempts, failed]. The attempts recorded before the skill was kept have no skill to count.
        Map<String, int[]> stats = new HashMap<>();
        for (AssessmentAttempt attempt : completedBetween(from, to)) {
            int[] counts = stats.computeIfAbsent(attempt.getSkillTag(), tag -> new int[2]);
            counts[0]++;
            if (!attempt.isPassed()) {
                counts[1]++;
            }
        }
        return stats.entrySet().stream()
                .filter(entry -> entry.getValue()[0] >= Math.max(1, minAttempts) && entry.getValue()[1] > 0)
                .map(entry -> new SkillDifficultyView(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                        round2((double) entry.getValue()[1] / entry.getValue()[0])))
                .sorted(Comparator.comparingDouble(SkillDifficultyView::failureRate).reversed()
                        .thenComparing(Comparator.comparingInt(SkillDifficultyView::attempts).reversed())
                        .thenComparing(SkillDifficultyView::skillTag))
                .limit(limit)
                .toList();
    }

    @Override
    public List<SubTopicDifficultyView> getSubTopicDifficulty(Instant from, Instant to, int limit) {
        if (!isValid(from, to, limit)) {
            return List.of();
        }

        Map<String, Integer> failedBySkill = new HashMap<>();
        Map<String, Map<String, Integer>> weakBySkill = new HashMap<>();
        for (AssessmentAttempt attempt : completedBetween(from, to)) {
            if (attempt.isPassed()) {
                continue;
            }
            failedBySkill.merge(attempt.getSkillTag(), 1, Integer::sum);
            if (attempt.getWeakSubTopic() != null) {
                weakBySkill.computeIfAbsent(attempt.getSkillTag(), tag -> new HashMap<>())
                        .merge(attempt.getWeakSubTopic(), 1, Integer::sum);
            }
        }
        return weakBySkill.entrySet().stream()
                .flatMap(skill -> skill.getValue().entrySet().stream()
                        .map(subTopic -> new SubTopicDifficultyView(skill.getKey(), subTopic.getKey(),
                                subTopic.getValue(),
                                round2((double) subTopic.getValue() / failedBySkill.get(skill.getKey())))))
                .sorted(Comparator.comparingInt(SubTopicDifficultyView::weakCount).reversed()
                        .thenComparing(Comparator.comparingDouble(SubTopicDifficultyView::shareOfFailures).reversed())
                        .thenComparing(SubTopicDifficultyView::skillTag)
                        .thenComparing(SubTopicDifficultyView::subTopic))
                .limit(limit)
                .toList();
    }

    @Override
    public List<VerifierActivityView> getVerifierActivity() {
        Map<Integer, Double> averageSeconds = caseRepository.averageResolutionSecondsByVerifier();
        return profileRepository.findAll().stream()
                .sorted(Comparator.comparingInt(VerifierProfile::getVerifierUserId))
                .map(profile -> {
                    Double seconds = averageSeconds.get(profile.getVerifierUserId());
                    return new VerifierActivityView(profile.getVerifierUserId(), profile.isSuspended(),
                            seconds == null ? null : round2(seconds / SECONDS_PER_HOUR));
                })
                .toList();
    }

    private List<AssessmentAttempt> completedBetween(Instant from, Instant to) {
        return attemptRepository.findCompletedBetween(from, to).stream()
                .filter(attempt -> attempt.getSkillTag() != null)
                .toList();
    }

    private static boolean isValid(Instant from, Instant to, int limit) {
        return from != null && to != null && from.isBefore(to) && limit >= 1;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
