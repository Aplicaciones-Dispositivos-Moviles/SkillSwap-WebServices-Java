package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.EvaluationStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.PreliminaryEvaluation;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;

/**
 * The delivery of a mini-project: the link to the repository of the student. A mini-project is delivered once. The
 * rules that depend on other contexts (the owner, the deadline, the node) are checked by the application layer; here
 * only the delivery itself is valid or not.
 *
 * <p>The delivery is received as {@link EvaluationStatus#PENDING}. The preliminary evaluation of the AI reacts to the
 * {@code MiniProjectSubmitted} event and settles it once (US19): approved, escalated to a verifier, or left without
 * review because the plan had no escalations left. The score, confidence and feedback of the AI are kept with it;
 * they are empty when the AI could not evaluate it.
 */
@Entity
@Table(name = "mini_project_submissions")
public class MiniProjectSubmission {

    public static final int MAX_REPOSITORY_URL_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    /** The mini-project of Learning Path Engine that is delivered (a plain id: contexts do not share entities). */
    @Column(name = "mini_project_id", nullable = false, unique = true)
    private int miniProjectId;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "path_node_id", nullable = false)
    private int pathNodeId;

    @Column(name = "skill_tag", nullable = false, length = 100)
    private String skillTag;

    @Column(name = "repository_url", nullable = false, length = 500)
    private String repositoryUrl;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    /** Stored as "Pending", "Approved", "Escalated" or "EscalationLimitReached". */
    @Column(name = "evaluation_status", nullable = false, length = 30)
    private EvaluationStatus evaluationStatus;

    /** The score the AI gave (0 to 100); null while pending or when the AI could not evaluate it. */
    @Column(name = "preliminary_score")
    private Integer preliminaryScore;

    /** How sure the AI was of the score (0 to 100); null while pending or when the AI could not evaluate it. */
    @Column(name = "preliminary_confidence")
    private Integer preliminaryConfidence;

    @Column(name = "preliminary_feedback", length = 1000)
    private String preliminaryFeedback;

    /** When the preliminary evaluation settled the delivery; null while it is pending. */
    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    /** Required by JPA. */
    protected MiniProjectSubmission() {
    }

    /**
     * @throws DomainException when an id is not valid, the skill is empty, or the repository link is empty, too long
     *                         or not an http(s) URL
     */
    public MiniProjectSubmission(int miniProjectId, int studentId, int pathNodeId, String skillTag,
                                 String repositoryUrl) {
        if (miniProjectId <= 0) {
            throw new DomainException("The delivery must refer to a valid mini-project.");
        }
        if (studentId <= 0) {
            throw new DomainException("The delivery must refer to a valid student.");
        }
        if (pathNodeId <= 0) {
            throw new DomainException("The delivery must refer to a valid path node.");
        }
        String skill = skillTag == null ? "" : skillTag.strip();
        if (skill.isEmpty()) {
            throw new DomainException("The skill of the delivery is required.");
        }

        String url = repositoryUrl == null ? "" : repositoryUrl.strip();
        if (url.isEmpty()) {
            throw new DomainException("The repository link cannot be empty.");
        }
        if (url.length() > MAX_REPOSITORY_URL_LENGTH) {
            throw new DomainException(
                    "The repository link cannot exceed %d characters.".formatted(MAX_REPOSITORY_URL_LENGTH));
        }
        if (!isHttpUrl(url)) {
            throw new DomainException("The repository link must be a valid http or https URL.");
        }

        this.miniProjectId = miniProjectId;
        this.studentId = studentId;
        this.pathNodeId = pathNodeId;
        this.skillTag = skill;
        this.repositoryUrl = url;
        this.submittedAt = Instant.now();
        this.evaluationStatus = EvaluationStatus.PENDING;
    }

    /** Whether the preliminary evaluation has not settled the delivery yet. */
    public boolean isAwaitingEvaluation() {
        return evaluationStatus == EvaluationStatus.PENDING;
    }

    /**
     * The AI approved the delivery (US19 escenario 2): its score reaches the minimum of the rubric with enough
     * confidence, so no verifier reviews it.
     *
     * @throws DomainException when the delivery was already settled or the evaluation is not sufficient
     */
    public MiniProjectSubmission approve(PreliminaryEvaluation evaluation, int passingScore) {
        requireAwaitingEvaluation();
        if (evaluation == null || !evaluation.isSufficientFor(passingScore)) {
            throw new DomainException("Only a sufficient preliminary evaluation approves a delivery.");
        }
        settle(EvaluationStatus.APPROVED, evaluation);
        return this;
    }

    /**
     * The delivery goes to a verifier (US19 escenario 3): the evaluation did not reach the minimum, its confidence was
     * low, or the AI could not evaluate it (null).
     *
     * @throws DomainException when the delivery was already settled
     */
    public MiniProjectSubmission escalate(PreliminaryEvaluation evaluation) {
        requireAwaitingEvaluation();
        settle(EvaluationStatus.ESCALATED, evaluation);
        return this;
    }

    /**
     * The delivery needed a verifier but the plan of the student had no escalations left this month, so no case is
     * opened for it.
     *
     * @param evaluation the evaluation of the AI, or null when it could not evaluate it
     * @throws DomainException when the delivery was already settled
     */
    public MiniProjectSubmission keepWithoutReview(PreliminaryEvaluation evaluation) {
        requireAwaitingEvaluation();
        settle(EvaluationStatus.ESCALATION_LIMIT_REACHED, evaluation);
        return this;
    }

    private void requireAwaitingEvaluation() {
        if (!isAwaitingEvaluation()) {
            throw new DomainException("The preliminary evaluation of the delivery was already recorded.");
        }
    }

    private void settle(EvaluationStatus status, PreliminaryEvaluation evaluation) {
        this.evaluationStatus = status;
        this.preliminaryScore = evaluation == null ? null : evaluation.score();
        this.preliminaryConfidence = evaluation == null ? null : evaluation.confidence();
        this.preliminaryFeedback = evaluation == null || evaluation.feedback().isEmpty() ? null : evaluation.feedback();
        this.evaluatedAt = Instant.now();
    }

    private static boolean isHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return (scheme.equals("http") || scheme.equals("https")) && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    public Integer getId() {
        return id;
    }

    public int getMiniProjectId() {
        return miniProjectId;
    }

    public int getStudentId() {
        return studentId;
    }

    public int getPathNodeId() {
        return pathNodeId;
    }

    public String getSkillTag() {
        return skillTag;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public EvaluationStatus getEvaluationStatus() {
        return evaluationStatus;
    }

    /** The score the AI gave (0 to 100); null while pending or when the AI could not evaluate it. */
    public Integer getPreliminaryScore() {
        return preliminaryScore;
    }

    /** How sure the AI was of its score (0 to 100); null while pending or when the AI could not evaluate it. */
    public Integer getPreliminaryConfidence() {
        return preliminaryConfidence;
    }

    /** The feedback of the AI for the student; null when there is none. */
    public String getPreliminaryFeedback() {
        return preliminaryFeedback;
    }

    /** When the preliminary evaluation settled the delivery; null while it is pending. */
    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
