package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.FinalDemonstration;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.FinalDemonstrationRepository;
import com.innovify.skillswap.support.PostgresIntegrationTest;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionOperations;

class FinalDemonstrationPersistenceTest extends PostgresIntegrationTest {

    private static final Instant IN_TWO_DAYS = Instant.now().plus(Duration.ofDays(2));

    @Autowired
    private FinalDemonstrationRepository demonstrations;

    @Autowired
    private TransactionOperations transactions;

    private FinalDemonstration add(int studentId, int pathId, int verifierId) {
        return demonstrations.save(new FinalDemonstration(studentId, pathId, "java", verifierId,
                DemonstrationModality.ADVANCED_PROJECT, IN_TWO_DAYS));
    }

    @Test
    void demonstration_isStoredWithItsTextValues() throws SQLException {
        FinalDemonstration saved = add(1, 5, 9);

        assertThat(queryString("SELECT modality FROM final_demonstrations WHERE id = " + saved.getId()))
                .isEqualTo("AdvancedProject");
        assertThat(queryString("SELECT status FROM final_demonstrations WHERE id = " + saved.getId()))
                .isEqualTo("Scheduled");

        FinalDemonstration loaded = demonstrations.findById(saved.getId()).orElseThrow();
        assertThat(loaded.getModality()).isEqualTo(DemonstrationModality.ADVANCED_PROJECT);
        assertThat(loaded.getStatus()).isEqualTo(DemonstrationStatus.SCHEDULED);
        assertThat(loaded.getScheduledAt()).isEqualTo(saved.getScheduledAt());
    }

    @Test
    void secondActiveDemonstrationOfAPath_isRejectedByTheDatabase() {
        add(1, 5, 9);

        assertThatThrownBy(() -> add(1, 5, 10)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cancelledDemonstration_letsThePathScheduleAnother() {
        FinalDemonstration first = add(1, 5, 9);
        first.cancel();
        demonstrations.save(first);

        assertThat(demonstrations.findBlockingByLearningPathId(5)).isEmpty();
        assertThat(add(1, 5, 10).getId()).isNotNull();
        assertThat(demonstrations.findBlockingByLearningPathId(5)).isPresent();
    }

    @Test
    void studentCannotBeTheVerifier_inTheDatabaseToo() {
        assertThatThrownBy(() -> execute("INSERT INTO final_demonstrations (student_id, learning_path_id, skill_tag, "
                + "verifier_user_id, modality, scheduled_at, status, created_at, updated_at) "
                + "VALUES (3, 5, 'java', 3, 'AdvancedProject', now(), 'Scheduled', now(), now())"))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void queries_listByStudentAndByVerifier() {
        FinalDemonstration older = add(1, 5, 9);
        FinalDemonstration newer = add(1, 6, 9);
        FinalDemonstration cancelled = add(2, 7, 9);
        cancelled.cancel();
        demonstrations.save(cancelled);

        assertThat(demonstrations.findByStudentId(1)).extracting(FinalDemonstration::getId)
                .containsExactly(newer.getId(), older.getId());
        assertThat(demonstrations.findByVerifierUserId(9)).extracting(FinalDemonstration::getId)
                .containsExactlyInAnyOrder(older.getId(), newer.getId());
    }

    @Test
    void countActive_countsScheduledDemonstrationsPerVerifier() {
        add(1, 5, 9);
        add(2, 6, 9);
        FinalDemonstration cancelled = add(3, 7, 9);
        cancelled.cancel();
        demonstrations.save(cancelled);
        add(4, 8, 10);

        Map<Integer, Integer> counts = demonstrations.countActiveByVerifierUserIds(List.of(9, 10, 11));

        assertThat(counts).containsEntry(9, 2).containsEntry(10, 1).doesNotContainKey(11);
        assertThat(demonstrations.countActiveByVerifierUserIds(List.of())).isEmpty();
    }

    @Test
    void findByIdForUpdate_readsInsideATransaction() {
        FinalDemonstration saved = add(1, 5, 9);

        Integer id = transactions.execute(status -> demonstrations.findByIdForUpdate(saved.getId())
                .orElseThrow().getId());

        assertThat(id).isEqualTo(saved.getId());
    }

    @Test
    void videoAndReview_arePersisted() {
        FinalDemonstration saved = add(1, 5, 9);
        saved.attachVideo("demonstrations/" + saved.getId() + "/abc.mp4", "video/mp4", 4096);
        demonstrations.save(saved);

        FinalDemonstration withVideo = demonstrations.findById(saved.getId()).orElseThrow();
        assertThat(withVideo.getStatus()).isEqualTo(DemonstrationStatus.VIDEO_SUBMITTED);
        assertThat(withVideo.getVideoStorageReference()).endsWith("/abc.mp4");
        assertThat(withVideo.getVideoSizeBytes()).isEqualTo(4096L);
        assertThat(withVideo.getVideoSubmittedAt()).isNotNull();

        withVideo.review(85, "Clear and complete.");
        demonstrations.save(withVideo);

        FinalDemonstration reviewed = demonstrations.findById(saved.getId()).orElseThrow();
        assertThat(reviewed.getStatus()).isEqualTo(DemonstrationStatus.APPROVED);
        assertThat(reviewed.getScore()).isEqualTo(85);
        assertThat(reviewed.getFeedback()).isEqualTo("Clear and complete.");
        assertThat(reviewed.getReviewedAt()).isNotNull();
        assertThat(demonstrations.findBlockingByLearningPathId(5)).isPresent();
    }

    @Test
    void rejectedDemonstration_letsThePathScheduleAnother() {
        FinalDemonstration saved = add(1, 5, 9);
        saved.attachVideo("demonstrations/x/abc.mp4", "video/mp4", 10);
        saved.review(40, "Not enough.");
        demonstrations.save(saved);

        assertThat(demonstrations.findBlockingByLearningPathId(5)).isEmpty();
        assertThat(add(1, 5, 10).getId()).isNotNull();
    }

    @Test
    void submittedOrReviewedStatus_withoutTheVideoOrScore_isRejectedByTheDatabase() throws SQLException {
        FinalDemonstration saved = add(1, 5, 9);

        assertThatThrownBy(() -> execute("UPDATE final_demonstrations SET status = 'VideoSubmitted' WHERE id = "
                + saved.getId())).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE final_demonstrations SET status = 'Approved', "
                + "video_storage_reference = 'x.mp4' WHERE id = " + saved.getId())).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE final_demonstrations SET score = 101 WHERE id = " + saved.getId()))
                .isInstanceOf(SQLException.class);
    }
}
