package com.innovify.skillswap.assessmentpeerreview.application.internal.commandservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeDomainEventPublisher;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeFileStorage;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.FakeFinalDemonstrationRepository;
import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.assessmentpeerreview.domain.model.AssessmentPeerReviewError;
import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.ReviewFinalDemonstrationCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.SubmitDemonstrationVideoCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.events.FinalDemonstrationReviewed;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.shared.application.Result;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionOperations;

class DemonstrationEvaluationCommandServiceImplTest {

    private static final int STUDENT = 1;
    private static final int VERIFIER = 9;

    private final FakeFinalDemonstrationRepository demonstrations = new FakeFinalDemonstrationRepository();
    private final FakeFileStorage storage = new FakeFileStorage();
    private final FakeDomainEventPublisher events = new FakeDomainEventPublisher();
    private DemonstrationEvaluationCommandServiceImpl service;
    private FinalDemonstration demonstration;

    @BeforeEach
    void setUp() {
        LocaleContextHolder.setLocale(Locale.US);
        service = new DemonstrationEvaluationCommandServiceImpl(demonstrations, storage, events,
                TransactionOperations.withoutTransaction(), TestMessages.source());
        demonstration = demonstrations.save(new FinalDemonstration(STUDENT, 5, "java", VERIFIER,
                DemonstrationModality.ADVANCED_PROJECT, Instant.now().plus(Duration.ofDays(3))));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    /** A few bytes that start like an MP4 file: a box of 24 bytes of type "ftyp" with the brand "isom". */
    private static byte[] mp4() {
        byte[] content = new byte[64];
        content[3] = 24;
        content[4] = 'f';
        content[5] = 't';
        content[6] = 'y';
        content[7] = 'p';
        content[8] = 'i';
        content[9] = 's';
        content[10] = 'o';
        content[11] = 'm';
        return content;
    }

    private static byte[] quickTime() {
        byte[] content = mp4();
        content[8] = 'q';
        content[9] = 't';
        content[10] = ' ';
        content[11] = ' ';
        return content;
    }

    private static byte[] webm() {
        byte[] content = new byte[64];
        content[0] = 0x1A;
        content[1] = 0x45;
        content[2] = (byte) 0xDF;
        content[3] = (byte) 0xA3;
        return content;
    }

    private Result<FinalDemonstration> upload(byte[] content, String contentType) {
        return service.handle(new SubmitDemonstrationVideoCommand(demonstration.getId(), STUDENT, content,
                contentType));
    }

    private Result<FinalDemonstration> review(int verifier, Integer score, String feedback) {
        return service.handle(new ReviewFinalDemonstrationCommand(demonstration.getId(), verifier, score, feedback));
    }

    private void assertFailure(Result<?> result, AssessmentPeerReviewError expected) {
        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(expected);
        assertThat(result.message()).isNotBlank().isNotEqualTo(expected.name());
    }

    // ---------- Video (US29) ----------

    @Test
    void upload_ofAnMp4_storesItAndMarksTheVideoSubmitted() {
        Result<FinalDemonstration> result = upload(mp4(), "video/mp4");

        assertThat(result.isSuccess()).isTrue();
        FinalDemonstration saved = result.value();
        assertThat(saved.getStatus()).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
        assertThat(saved.getVideoContentType()).isEqualTo("video/mp4");
        assertThat(saved.getVideoSizeBytes()).isEqualTo(64L);
        assertThat(saved.getVideoStorageReference()).startsWith("demonstrations/" + saved.getId() + "/");
        assertThat(storage.uploads()).singleElement().satisfies(upload -> {
            assertThat(upload.contentType()).isEqualTo("video/mp4");
            assertThat(upload.length()).isEqualTo(64);
        });
        assertThat(storage.deleted()).isEmpty();
    }

    @Test
    void upload_acceptsWebMAndQuickTimeToo() {
        assertThat(upload(webm(), "video/webm").isSuccess()).isTrue();
        assertThat(upload(quickTime(), "VIDEO/QuickTime ").isSuccess()).isTrue();
        assertThat(demonstration.getVideoContentType()).isEqualTo("video/quicktime");
    }

    @Test
    void upload_again_replacesTheVideoAndDeletesTheOldFile() {
        String first = upload(mp4(), "video/mp4").value().getVideoStorageReference();

        FinalDemonstration second = upload(webm(), "video/webm").value();

        assertThat(second.getVideoStorageReference()).isNotEqualTo(first).endsWith(".webm");
        assertThat(storage.deleted()).containsExactly(first);
        assertThat(second.getStatus()).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
    }

    @Test
    void upload_withoutContent_orTooLarge_isRejectedBeforeStoring() {
        assertFailure(upload(null, "video/mp4"), AssessmentPeerReviewError.VIDEO_REQUIRED);
        assertFailure(upload(new byte[0], "video/mp4"), AssessmentPeerReviewError.VIDEO_REQUIRED);
        assertFailure(upload(new byte[DemonstrationEvaluationCommandServiceImpl.MAX_VIDEO_SIZE_BYTES + 1],
                "video/mp4"), AssessmentPeerReviewError.VIDEO_TOO_LARGE);
        assertThat(storage.uploads()).isEmpty();
    }

    @Test
    void upload_whoseRealTypeIsNotTheDeclaredOne_isRejected() {
        assertFailure(upload(mp4(), "video/webm"), AssessmentPeerReviewError.UNSUPPORTED_VIDEO_TYPE);
        assertFailure(upload(new byte[64], "video/mp4"), AssessmentPeerReviewError.UNSUPPORTED_VIDEO_TYPE);
        assertFailure(upload(mp4(), null), AssessmentPeerReviewError.UNSUPPORTED_VIDEO_TYPE);
        assertThat(storage.uploads()).isEmpty();
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.SCHEDULED);
    }

    @Test
    void upload_ofAnotherStudent_unknown_orClosedDemonstration_isRejected() {
        assertFailure(service.handle(new SubmitDemonstrationVideoCommand(demonstration.getId(), 2, mp4(),
                "video/mp4")), AssessmentPeerReviewError.NOT_DEMONSTRATION_OWNER);
        assertFailure(service.handle(new SubmitDemonstrationVideoCommand(99, STUDENT, mp4(), "video/mp4")),
                AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);

        demonstration.cancel();
        assertFailure(upload(mp4(), "video/mp4"), AssessmentPeerReviewError.DEMONSTRATION_CLOSED);
        assertThat(storage.uploads()).isEmpty();
    }

    @Test
    void upload_afterTheReview_isRejected() {
        upload(mp4(), "video/mp4");
        review(VERIFIER, 90, null);

        assertFailure(upload(mp4(), "video/mp4"), AssessmentPeerReviewError.DEMONSTRATION_CLOSED);
    }

    @Test
    void upload_whenTheStorageFails_returnsAStorageErrorAndKeepsTheDemonstration() {
        storage.failOnUpload(new IllegalStateException("Cloudinary upload failed with HTTP 500"));

        assertFailure(upload(mp4(), "video/mp4"), AssessmentPeerReviewError.VIDEO_STORAGE_ERROR);
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.SCHEDULED);
        assertThat(demonstration.hasVideo()).isFalse();
    }

    @Test
    void upload_whenTheDatabaseFails_deletesTheStoredFile() {
        demonstrations.failOnSave(new DataAccessResourceFailureException("down"));

        assertFailure(upload(mp4(), "video/mp4"), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(storage.uploads()).hasSize(1);
        assertThat(storage.deleted()).hasSize(1);
    }

    @Test
    void upload_whenDeletingTheOldFileFails_stillSucceeds() {
        upload(mp4(), "video/mp4");
        storage.failOnDelete(new IllegalStateException("boom"));

        assertThat(upload(webm(), "video/webm").isSuccess()).isTrue();
    }

    // ---------- Review ----------

    @Test
    void review_withThePassingScore_approvesAndPublishesTheEvent() {
        upload(mp4(), "video/mp4");

        Result<FinalDemonstration> result = review(VERIFIER, 85, "Well done.");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value().getStatus()).isEqualTo(DemonstrationStatus.APPROVED);
        assertThat(result.value().getScore()).isEqualTo(85);
        assertThat(events.published()).containsExactly(
                new FinalDemonstrationReviewed(demonstration.getId(), STUDENT, VERIFIER, "java", 85, true));
        assertThat(demonstrations.lockedDemonstrations()).contains(demonstration.getId());
    }

    @Test
    void review_underThePassingScore_rejectsWithFeedback() {
        upload(mp4(), "video/mp4");

        Result<FinalDemonstration> result = review(VERIFIER, 40, "The demo does not run.");

        assertThat(result.value().getStatus()).isEqualTo(DemonstrationStatus.REJECTED);
        assertThat(events.published()).containsExactly(
                new FinalDemonstrationReviewed(demonstration.getId(), STUDENT, VERIFIER, "java", 40, false));
    }

    @Test
    void review_withInvalidData_isRejectedAndKeepsTheVideoSubmitted() {
        upload(mp4(), "video/mp4");

        assertFailure(review(VERIFIER, null, "x"), AssessmentPeerReviewError.INVALID_DEMONSTRATION_SCORE);
        assertFailure(review(VERIFIER, 101, "x"), AssessmentPeerReviewError.INVALID_DEMONSTRATION_SCORE);
        assertFailure(review(VERIFIER, -1, "x"), AssessmentPeerReviewError.INVALID_DEMONSTRATION_SCORE);
        assertFailure(review(VERIFIER, 30, "  "), AssessmentPeerReviewError.DEMONSTRATION_FEEDBACK_REQUIRED);
        assertFailure(review(VERIFIER, 90, "x".repeat(1001)),
                AssessmentPeerReviewError.DEMONSTRATION_FEEDBACK_TOO_LONG);
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void review_byAnotherUser_beforeTheVideo_orTwice_isRejected() {
        assertFailure(review(VERIFIER, 90, null), AssessmentPeerReviewError.VIDEO_NOT_SUBMITTED);

        upload(mp4(), "video/mp4");
        assertFailure(review(10, 90, null), AssessmentPeerReviewError.NOT_DEMONSTRATION_VERIFIER);
        assertFailure(review(STUDENT, 90, null), AssessmentPeerReviewError.NOT_DEMONSTRATION_VERIFIER);

        assertThat(review(VERIFIER, 90, null).isSuccess()).isTrue();
        events.published().clear();
        assertFailure(review(VERIFIER, 10, "No"), AssessmentPeerReviewError.DEMONSTRATION_ALREADY_REVIEWED);
        assertThat(events.published()).isEmpty();
        assertThat(demonstration.getStatus()).isEqualTo(DemonstrationStatus.APPROVED);
    }

    @Test
    void review_ofAnUnknownDemonstration_isRejected() {
        assertFailure(service.handle(new ReviewFinalDemonstrationCommand(99, VERIFIER, 90, null)),
                AssessmentPeerReviewError.DEMONSTRATION_NOT_FOUND);
    }

    @Test
    void review_whenTheDatabaseFails_returnsADatabaseErrorAndPublishesNothing() {
        upload(mp4(), "video/mp4");
        demonstrations.failOnSave(new DataAccessResourceFailureException("down"));

        assertFailure(review(VERIFIER, 90, null), AssessmentPeerReviewError.DATABASE_ERROR);
        assertThat(events.published()).isEmpty();
    }

    @Test
    void detectContentType_readsTheFirstBytes() {
        assertThat(DemonstrationEvaluationCommandServiceImpl.detectContentType(mp4())).isEqualTo("video/mp4");
        assertThat(DemonstrationEvaluationCommandServiceImpl.detectContentType(quickTime()))
                .isEqualTo("video/quicktime");
        assertThat(DemonstrationEvaluationCommandServiceImpl.detectContentType(webm())).isEqualTo("video/webm");
        assertThat(DemonstrationEvaluationCommandServiceImpl.detectContentType(new byte[]{1, 2, 3})).isNull();
        assertThat(DemonstrationEvaluationCommandServiceImpl.detectContentType(null)).isNull();
    }
}
