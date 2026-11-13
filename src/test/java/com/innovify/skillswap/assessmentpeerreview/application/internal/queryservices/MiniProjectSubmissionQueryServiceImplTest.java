package com.innovify.skillswap.assessmentpeerreview.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeMiniProjectSubmissionRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeVerificationCaseRepository;
import com.innovify.skillswap.assessmentpeerreview.application.queryservices.MiniProjectSubmissionDetail;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.MiniProjectSubmission;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.queries.GetMiniProjectSubmissionDetailQuery;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.EvaluationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import org.junit.jupiter.api.Test;

class MiniProjectSubmissionQueryServiceImplTest {

    private static final String URL = "https://github.com/ana/http-basics";

    private final FakeMiniProjectSubmissionRepository submissions = new FakeMiniProjectSubmissionRepository();
    private final FakeVerificationCaseRepository cases = new FakeVerificationCaseRepository();
    private final MiniProjectSubmissionQueryServiceImpl service =
            new MiniProjectSubmissionQueryServiceImpl(submissions, cases);

    private MiniProjectSubmission deliver(int miniProjectId) {
        return submissions.save(new MiniProjectSubmission(miniProjectId, 1, 10, "http-basics", URL));
    }

    @Test
    void handle_anEscalatedDelivery_returnsItWithItsCase() {
        MiniProjectSubmission delivery = submissions.save(
                deliver(3).escalate(new PreliminaryEvaluation(55, 80, "Add tests.")));
        cases.save(new VerificationCase(9, 1, 10, "http-basics", CaseType.QUIZ));
        VerificationCase opened = cases.save(VerificationCase.forMiniProject(delivery.getId(), 1, 10, "http-basics",
                URL, null));

        MiniProjectSubmissionDetail detail = service.handle(new GetMiniProjectSubmissionDetailQuery(delivery.getId()))
                .orElseThrow();

        assertThat(detail.submission()).isSameAs(delivery);
        assertThat(detail.submission().getEvaluationStatus()).isEqualTo(EvaluationStatus.ESCALATED);
        assertThat(detail.verificationCase()).isSameAs(opened);
    }

    @Test
    void handle_aPendingOrApprovedDelivery_hasNoCase() {
        MiniProjectSubmission pending = deliver(3);
        MiniProjectSubmission approved = submissions.save(
                deliver(4).approve(new PreliminaryEvaluation(85, 90, ""), 70));

        assertThat(service.handle(new GetMiniProjectSubmissionDetailQuery(pending.getId())).orElseThrow()
                .verificationCase()).isNull();
        assertThat(service.handle(new GetMiniProjectSubmissionDetailQuery(approved.getId())).orElseThrow()
                .verificationCase()).isNull();
    }

    @Test
    void handle_anUnknownDelivery_returnsEmpty() {
        assertThat(service.handle(new GetMiniProjectSubmissionDetailQuery(99))).isEmpty();
    }
}
