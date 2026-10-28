package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.ReevaluationCandidate;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.ReevaluationQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetReevaluationCandidatesQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReevaluationSourceType;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.ReevaluationRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.reputation.application.acl.ReputationContextFacade;
import com.innovify.skillswap.shared.application.Result;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * The approved results that a Verificador senior can order to evaluate again (US37). The senior is the same kind of
 * verifier the reevaluation command asks for: an enabled verifier whom Reputation considers senior. Nothing about the
 * student leaves this service; only the name of the verifier who approved a case does.
 *
 * <p>Each source is read newest first with some margin ({@value #FETCH_FACTOR} times the limit), because the ones
 * that were reevaluated or have a case in progress are discarded afterwards; the answer has at most the limit.
 */
@Service
public class ReevaluationQueryServiceImpl implements ReevaluationQueryService {

    static final int FETCH_FACTOR = 3;

    private final AssessmentAttemptRepository attemptRepository;
    private final VerificationCaseRepository caseRepository;
    private final VerifierProfileRepository profileRepository;
    private final ReevaluationRepository reevaluationRepository;
    private final ReputationContextFacade reputationFacade;
    private final LearningPathContextFacade learningPathFacade;
    private final IamContextFacade iamFacade;
    private final MessageSource messageSource;

    public ReevaluationQueryServiceImpl(AssessmentAttemptRepository attemptRepository,
                                        VerificationCaseRepository caseRepository,
                                        VerifierProfileRepository profileRepository,
                                        ReevaluationRepository reevaluationRepository,
                                        ReputationContextFacade reputationFacade,
                                        LearningPathContextFacade learningPathFacade,
                                        IamContextFacade iamFacade,
                                        MessageSource messageSource) {
        this.attemptRepository = attemptRepository;
        this.caseRepository = caseRepository;
        this.profileRepository = profileRepository;
        this.reevaluationRepository = reevaluationRepository;
        this.reputationFacade = reputationFacade;
        this.learningPathFacade = learningPathFacade;
        this.iamFacade = iamFacade;
        this.messageSource = messageSource;
    }

    @Override
    public Result<List<ReevaluationCandidate>> handle(GetReevaluationCandidatesQuery query) {
        boolean enabledVerifier = profileRepository.findByUserId(query.seniorUserId())
                .map(VerifierProfile::isVerified)
                .orElse(false);
        if (!enabledVerifier || !reputationFacade.isSeniorVerifier(query.seniorUserId())) {
            String code = ErrorCodes.of(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER);
            return Result.failure(AssessmentPeerReviewError.NOT_SENIOR_VERIFIER,
                    messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale()));
        }

        int fetch = query.limit() * FETCH_FACTOR;
        List<ReevaluationCandidate> candidates = new ArrayList<>(attemptCandidates(query, fetch));
        candidates.addAll(caseCandidates(query, fetch));
        candidates.sort(Comparator.comparing(ReevaluationCandidate::resolvedAt).reversed()
                .thenComparing(Comparator.comparingInt(ReevaluationCandidate::sourceId).reversed()));
        return Result.success(candidates.stream().limit(query.limit()).toList());
    }

    private List<ReevaluationCandidate> attemptCandidates(GetReevaluationCandidatesQuery query, int fetch) {
        List<AssessmentAttempt> attempts = attemptRepository.findRecentPassedExcludingStudent(
                query.seniorUserId(), query.skillTag(), fetch);
        Set<Integer> reevaluated = reevaluationRepository.findReevaluatedSourceIds(ReevaluationSourceType.ATTEMPT,
                attempts.stream().map(AssessmentAttempt::getId).toList());

        List<ReevaluationCandidate> candidates = new ArrayList<>();
        for (AssessmentAttempt attempt : attempts) {
            if (reevaluated.contains(attempt.getId())) {
                continue;
            }
            Optional<BlueprintView> blueprint = learningPathFacade.getBlueprint(attempt.getBlueprintId());
            if (blueprint.isEmpty()
                    || caseRepository.findOpenByStudentAndNode(attempt.getStudentId(),
                            blueprint.get().pathNodeId()).isPresent()) {
                continue;
            }
            String skillTag = attempt.getSkillTag() != null ? attempt.getSkillTag() : blueprint.get().skillTag();
            candidates.add(new ReevaluationCandidate(ReevaluationSourceType.ATTEMPT, attempt.getId(), skillTag,
                    null, attempt.getCompletedAt(), null));
        }
        return candidates;
    }

    private List<ReevaluationCandidate> caseCandidates(GetReevaluationCandidatesQuery query, int fetch) {
        Map<Integer, Optional<String>> names = new HashMap<>();
        List<ReevaluationCandidate> candidates = new ArrayList<>();
        for (VerificationCase verificationCase : caseRepository.findRecentApprovedExcludingStudent(
                query.seniorUserId(), query.skillTag(), fetch)) {
            if (!verificationCase.canBeReevaluated() || verificationCase.getResolvedAt() == null
                    || caseRepository.findOpenByStudentAndNode(verificationCase.getStudentId(),
                            verificationCase.getPathNodeId()).isPresent()) {
                continue;
            }
            Integer approver = verificationCase.getVerifierUserId();
            String approverName = approver == null ? null
                    : names.computeIfAbsent(approver, iamFacade::getRegisteredFullName).orElse(null);
            Instant resolvedAt = verificationCase.getResolvedAt();
            candidates.add(new ReevaluationCandidate(ReevaluationSourceType.CASE, verificationCase.getId(),
                    verificationCase.getSkillTag(), verificationCase.getCaseType(), resolvedAt, approverName));
        }
        return candidates;
    }
}
