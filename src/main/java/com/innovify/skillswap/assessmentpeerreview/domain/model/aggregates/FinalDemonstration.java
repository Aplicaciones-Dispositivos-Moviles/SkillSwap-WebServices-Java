package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationModality;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.DemonstrationStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * The final demonstration of a completed path: the student chooses a modality and a moment, and a verifier enabled
 * in a skill of the path is assigned (US28). The rules that depend on other contexts (the path is completed, who
 * owns it, which verifier is available) are checked by the application layer; here only the demonstration itself is
 * valid or not.
 *
 * <p>It is scheduled at least {@link #MIN_LEAD_TIME} ahead and can be rescheduled or cancelled while it is
 * {@link DemonstrationStatus#SCHEDULED}. The student then uploads the video of the demonstration (US29), which can be
 * replaced until the verifier reviews it. The verifier scores it with the rubric: {@link #PASSING_SCORE} or more
 * approves it, less rejects it and needs a feedback.
 */
@Entity
@Table(name = "final_demonstrations")
public class FinalDemonstration {

    /** How far in the future the demonstration must be scheduled. */
    public static final Duration MIN_LEAD_TIME = Duration.ofHours(24);

    /** The score (0 to 100) from which the verifier approves the demonstration. */
    public static final int PASSING_SCORE = 70;

    public static final int MAX_FEEDBACK_LENGTH = 1000;
    public static final int MAX_VIDEO_REFERENCE_LENGTH = 300;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    /** The path of Learning Path Engine that was completed (a plain id: contexts do not share entities). */
    @Column(name = "learning_path_id", nullable = false)
    private int learningPathId;

    /** The skill of the path the assigned verifier is enabled in. */
    @Column(name = "skill_tag", nullable = false, length = 100)
    private String skillTag;

    @Column(name = "verifier_user_id", nullable = false)
    private int verifierUserId;

    /** Stored as "AdvancedProject" or "SupervisedExam". */
    @Column(name = "modality", nullable = false, length = 30)
    private DemonstrationModality modality;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    /** Stored as "Scheduled", "VideoSubmitted", "Approved", "Rejected" or "Cancelled". */
    @Column(name = "status", nullable = false, length = 30)
    private DemonstrationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Where the video is stored (null until it is uploaded). */
    @Column(name = "video_storage_reference", length = 300)
    private String videoStorageReference;

    @Column(name = "video_content_type", length = 50)
    private String videoContentType;

    @Column(name = "video_size_bytes")
    private Long videoSizeBytes;

    @Column(name = "video_submitted_at")
    private Instant videoSubmittedAt;

    /** The score the verifier gave with the rubric (0 to 100); null until the review. */
    @Column(name = "score")
    private Integer score;

    @Column(name = "feedback", length = 1000)
    private String feedback;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Required by JPA. */
    protected FinalDemonstration() {
    }

    /**
     * @throws DomainException when an id is not valid, the skill is empty, or the modality or the moment is missing
     */
    public FinalDemonstration(int studentId, int learningPathId, String skillTag, int verifierUserId,
                              DemonstrationModality modality, Instant scheduledAt) {
        if (studentId <= 0) {
            throw new DomainException("The demonstration must refer to a valid student.");
        }
        if (learningPathId <= 0) {
            throw new DomainException("The demonstration must refer to a valid path.");
        }
        String skill = skillTag == null ? "" : skillTag.strip();
        if (skill.isEmpty()) {
            throw new DomainException("The skill of the demonstration is required.");
        }
        if (verifierUserId <= 0) {
            throw new DomainException("The demonstration must have a valid verifier.");
        }
        if (verifierUserId == studentId) {
            throw new DomainException("A student cannot be the verifier of their own demonstration.");
        }
        if (modality == null) {
            throw new DomainException("The modality of the demonstration is required.");
        }
        if (scheduledAt == null) {
            throw new DomainException("The moment of the demonstration is required.");
        }

        this.studentId = studentId;
        this.learningPathId = learningPathId;
        this.skillTag = skill;
        this.verifierUserId = verifierUserId;
        this.modality = modality;
        this.scheduledAt = scheduledAt.truncatedTo(ChronoUnit.MICROS);
        this.status = DemonstrationStatus.SCHEDULED;
        this.createdAt = now();
        this.updatedAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getLearningPathId() {
        return learningPathId;
    }

    public String getSkillTag() {
        return skillTag;
    }

    public int getVerifierUserId() {
        return verifierUserId;
    }

    public DemonstrationModality getModality() {
        return modality;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public DemonstrationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getVideoStorageReference() {
        return videoStorageReference;
    }

    public String getVideoContentType() {
        return videoContentType;
    }

    public Long getVideoSizeBytes() {
        return videoSizeBytes;
    }

    public Instant getVideoSubmittedAt() {
        return videoSubmittedAt;
    }

    public Integer getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public boolean hasVideo() {
        return videoStorageReference != null;
    }

    /** Whether the student can still upload (or replace) the video: scheduled or with a video not reviewed yet. */
    public boolean acceptsVideo() {
        return status == DemonstrationStatus.SCHEDULED || status == DemonstrationStatus.VIDEO_SUBMITTED;
    }

    public boolean isVideoSubmitted() {
        return status == DemonstrationStatus.VIDEO_SUBMITTED;
    }

    public boolean isReviewed() {
        return status == DemonstrationStatus.APPROVED || status == DemonstrationStatus.REJECTED;
    }

    public boolean isOwnedBy(int userId) {
        return studentId == userId;
    }

    public boolean isAssignedTo(int userId) {
        return verifierUserId == userId;
    }

    public boolean isScheduled() {
        return status == DemonstrationStatus.SCHEDULED;
    }

    /** Whether {@code moment} is far enough from {@code now} to schedule a demonstration at it. */
    public static boolean isFarEnough(Instant moment, Instant now) {
        return moment != null && !moment.isBefore(now.plus(MIN_LEAD_TIME));
    }

    /**
     * Moves the demonstration to another moment.
     *
     * @throws DomainException when it is not scheduled or the moment is missing
     */
    public FinalDemonstration reschedule(Instant newMoment) {
        requireScheduled();
        if (newMoment == null) {
            throw new DomainException("The moment of the demonstration is required.");
        }
        this.scheduledAt = newMoment.truncatedTo(ChronoUnit.MICROS);
        this.updatedAt = now();
        return this;
    }

    /** @throws DomainException when it is not scheduled */
    public FinalDemonstration cancel() {
        requireScheduled();
        this.status = DemonstrationStatus.CANCELLED;
        this.updatedAt = now();
        return this;
    }

    /**
     * Stores the video of the demonstration, replacing the previous one while it is not reviewed.
     *
     * @throws DomainException when the demonstration does not accept a video or the data of the video is not valid
     */
    public FinalDemonstration attachVideo(String storageReference, String contentType, long sizeBytes) {
        if (!acceptsVideo()) {
            throw new DomainException("This demonstration cannot receive a video.");
        }
        String reference = storageReference == null ? "" : storageReference.strip();
        if (reference.isEmpty() || reference.length() > MAX_VIDEO_REFERENCE_LENGTH) {
            throw new DomainException("The video must have a valid storage reference.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new DomainException("The content type of the video is required.");
        }
        if (sizeBytes <= 0) {
            throw new DomainException("The video cannot be empty.");
        }

        this.videoStorageReference = reference;
        this.videoContentType = contentType.strip();
        this.videoSizeBytes = sizeBytes;
        this.videoSubmittedAt = now();
        this.status = DemonstrationStatus.VIDEO_SUBMITTED;
        this.updatedAt = videoSubmittedAt;
        return this;
    }

    /**
     * Records the review of the verifier: {@link #PASSING_SCORE} or more approves, less rejects and needs a feedback.
     *
     * @throws DomainException when the video was not submitted or was already reviewed, the score is not between 0 and
     *                         100, the feedback is too long, or a rejection has no feedback
     */
    public FinalDemonstration review(int score, String feedback) {
        if (!isVideoSubmitted()) {
            throw new DomainException("Only a demonstration with a submitted video can be reviewed.");
        }
        if (score < 0 || score > 100) {
            throw new DomainException("The score must be between 0 and 100.");
        }
        String notes = feedback == null ? "" : feedback.strip();
        if (notes.length() > MAX_FEEDBACK_LENGTH) {
            throw new DomainException("The feedback cannot exceed %d characters.".formatted(MAX_FEEDBACK_LENGTH));
        }
        if (score < PASSING_SCORE && notes.isEmpty()) {
            throw new DomainException("A rejection needs a feedback.");
        }

        this.score = score;
        this.feedback = notes.isEmpty() ? null : notes;
        this.status = score >= PASSING_SCORE ? DemonstrationStatus.APPROVED : DemonstrationStatus.REJECTED;
        this.reviewedAt = now();
        this.updatedAt = reviewedAt;
        return this;
    }

    private void requireScheduled() {
        if (status != DemonstrationStatus.SCHEDULED) {
            throw new DomainException("Only a scheduled demonstration can be changed.");
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
