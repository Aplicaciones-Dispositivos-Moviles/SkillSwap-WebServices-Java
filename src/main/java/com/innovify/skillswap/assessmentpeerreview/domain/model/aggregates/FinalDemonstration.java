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
 * {@link DemonstrationStatus#SCHEDULED}.
 */
@Entity
@Table(name = "final_demonstrations")
public class FinalDemonstration {

    /** How far in the future the demonstration must be scheduled. */
    public static final Duration MIN_LEAD_TIME = Duration.ofHours(24);

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

    private void requireScheduled() {
        if (status != DemonstrationStatus.SCHEDULED) {
            throw new DomainException("Only a scheduled demonstration can be changed.");
        }
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
