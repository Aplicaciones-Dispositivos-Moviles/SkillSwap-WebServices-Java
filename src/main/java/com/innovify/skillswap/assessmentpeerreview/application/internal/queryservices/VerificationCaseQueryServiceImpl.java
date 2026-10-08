package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import com.innovify.skillswap.assessmentpeerreview.application.queryservices.FailedQuestion;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseDetail;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.VerificationCaseQueryService;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.AssessmentAttempt;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseByIdQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCaseDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetVerificationCasesByVerifierQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.AssessmentAttemptRepository;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintQuestionView;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class VerificationCaseQueryServiceImpl implements VerificationCaseQueryService {

    private final VerificationCaseRepository caseRepository;
    private final AssessmentAttemptRepository attemptRepository;
    private final LearningPathContextFacade learningPathFacade;

    public VerificationCaseQueryServiceImpl(VerificationCaseRepository caseRepository,
                                            AssessmentAttemptRepository attemptRepository,
                                            LearningPathContextFacade learningPathFacade) {
        this.caseRepository = caseRepository;
        this.attemptRepository = attemptRepository;
        this.learningPathFacade = learningPathFacade;
    }

    @Override
    public Optional<VerificationCase> handle(GetVerificationCaseByIdQuery query) {
        return caseRepository.findById(query.caseId());
    }

    @Override
    public Optional<VerificationCaseDetail> handle(GetVerificationCaseDetailQuery query) {
        Optional<VerificationCase> verificationCase = caseRepository.findById(query.caseId());
        if (verificationCase.isEmpty()) {
            return Optional.empty();
        }

        Optional<AssessmentAttempt> attempt = attemptRepository.findById(verificationCase.get().getAttemptId());
        if (attempt.isEmpty()) {
            return Optional.empty();
        }

        List<FailedQuestion> failed = List.of();
        Optional<BlueprintView> blueprint = learningPathFacade.getBlueprint(attempt.get().getBlueprintId());
        if (blueprint.isPresent()) {
            List<BlueprintQuestionView> questions = blueprint.get().questions();

            // The correct answers only identify the failed questions; they are never copied to the result.
            List<Integer> incorrect = attempt.get().incorrectQuestionIndexes(
                    questions.stream().map(BlueprintQuestionView::correctAnswer).toList());
            failed = incorrect.stream()
                    .map(index -> new FailedQuestion(index + 1, questions.get(index).text(),
                            questions.get(index).answers(), attempt.get().getSelectedAnswers().get(index)))
                    .toList();
        }

        return Optional.of(new VerificationCaseDetail(verificationCase.get(), attempt.get(), failed));
    }

    @Override
    public List<VerificationCase> handle(GetVerificationCasesByVerifierQuery query) {
        return caseRepository.findByVerifierUserId(query.verifierUserId());
    }
}
