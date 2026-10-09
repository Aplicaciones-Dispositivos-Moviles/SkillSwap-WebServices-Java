package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseType;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDeadline;
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
 * Opened when an attempt is not approved, with the type of the work under review (a quiz or a mini-project). It
 * is assigned to an enabled verifier, who resolves it with a decision and the notes of the rubric. A student can
 * add evidence while it is open. A rejected case can be appealed once: it reopens and goes to a different
 * verifier, whose decision is final.
 *
 * <p>The review is due by the deadline of the plan the student had when the case was opened; a later change of plan
 * does not move it.
 */
@Entity
@Table(name = "verification_cases")
public class VerificationCase {

    public static final int MAX_EVIDENCE_URL_LENGTH = 500;
    public static final int MAX_RUBRIC_NOTES_LENGTH = 2000;
    /** How many times the student can appeal a rejected case. */
    public static final int MAX_APPEALS = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "attempt_id", nullable = false)
    private int attemptId;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "verifier_user_id")
    private Integer verifierUserId;

    @Column(name = "path_node_id", nullable = false)
    private int pathNodeId;

    @Column(name = "skill_tag", nullable = false, length = 100)
    private String skillTag;

    /** Stored as "Quiz" or "MiniProject"; it never changes once the case is opened. */
    @Column(name = "case_type", nullable = false, length = 20)
    private CaseType caseType;

    /** Stored as the text the C# API wrote ("Pending", "Assigned", "Resolved"). */
    @Column(name = "status", nullable = false, length = 20)
    private CaseStatus status;

    /** Stored as "Approved" or "Rejected"; null until the case is resolved. */
    @Column(name = "decision", length = 20)
    private ReviewDecision decision;

    @Column(name = "rubric_notes", length = 2000)
    private String rubricNotes;

    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    /** When the review is due; null for the cases opened before the plans set a deadline. */
    @Column(name = "review_due_at")
    private Instant reviewDueAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /** How many times the case was appealed; 0 for the cases that never were. */
    @Column(name = "appeal_count", nullable = false)
    private int appealCount;

    /** The verifier who resolved the case before the appeal; they cannot review it again. */
    @Column(name = "previous_verifier_user_id")
    private Integer previousVerifierUserId;

    /** Required by JPA. */
    protected VerificationCase() {
    }

    /**
     * Opens a case without a review deadline, as the cases opened before the plans set one.
     *
     * @throws DomainException when an id is not valid, the skill is empty or the type is missing
     */
    public VerificationCase(int attemptId, int studentId, int pathNodeId, String skillTag, CaseType caseType) {
        this(attemptId, studentId, pathNodeId, skillTag, caseType, null);
    }

    /**
     * Opens a case whose review is due by the deadline of the current plan of the student.
     *
     * @param reviewDeadline the deadline of the plan, or null for none
     * @throws DomainException when an id is not valid, the skill is empty or the type is missing
     */
    public VerificationCase(int attemptId, int studentId, int pathNodeId, String skillTag, CaseType caseType,
                            ReviewDeadline reviewDeadline) {
        if (attemptId <= 0) {
            throw new DomainException("The case must belong to a valid attempt.");
        }
        if (studentId <= 0) {
            throw new DomainException("The case must belong to a valid student.");
        }
        if (pathNodeId <= 0) {
            throw new DomainException("The case must belong to a valid path node.");
        }
        if (skillTag == null || skillTag.isBlank()) {
            throw new DomainException("The skill tag cannot be empty.");
        }
        if (caseType == null) {
            throw new DomainException("The case type is required.");
        }

        this.attemptId = attemptId;
        this.studentId = studentId;
        this.pathNodeId = pathNodeId;
        this.skillTag = skillTag.strip();
        this.caseType = caseType;
        this.status = CaseStatus.PENDING;
        this.openedAt = Instant.now();
        this.reviewDueAt = reviewDeadline == null ? null : reviewDeadline.dueFrom(openedAt);
    }

    public Integer getId() {
        return id;
    }

    public int getAttemptId() {
        return attemptId;
    }

    public int getStudentId() {
        return studentId;
    }

    /** The user id of the assigned verifier; null while the case is pending. */
    public Integer getVerifierUserId() {
        return verifierUserId;
    }

    public int getPathNodeId() {
        return pathNodeId;
    }

    public String getSkillTag() {
        return skillTag;
    }

    public CaseType getCaseType() {
        return caseType;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public ReviewDecision getDecision() {
        return decision;
    }

    public String getRubricNotes() {
        return rubricNotes;
    }

    public String getEvidenceUrl() {
        return evidenceUrl;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    /** When the review is due, fixed when the case was opened; null for the cases opened before the plans. */
    public Instant getReviewDueAt() {
        return reviewDueAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public int getAppealCount() {
        return appealCount;
    }

    /** The verifier who rejected the case before the appeal; null when it was never appealed. */
    public Integer getPreviousVerifierUserId() {
        return previousVerifierUserId;
    }

    public boolean isOpen() {
        return status != CaseStatus.RESOLVED;
    }

    public boolean isAssignedTo(int userId) {
        return verifierUserId != null && verifierUserId == userId;
    }

    /** True when the case is resolved with a rejection and the student has appeals left. */
    public boolean canBeAppealed() {
        return status == CaseStatus.RESOLVED && decision == ReviewDecision.REJECTED && hasAppealsLeft();
    }

    /**
     * The verifier whose rejection the current decision overturned: the one who rejected the case before the
     * appeal, when the new decision is an approval. Null for a case never appealed, a rejection that was
     * confirmed, or a case that is not resolved.
     */
    public Integer overturnedVerifierUserId() {
        return status == CaseStatus.RESOLVED && decision == ReviewDecision.APPROVED ? previousVerifierUserId : null;
    }

    public boolean hasAppealsLeft() {
        return appealCount < MAX_APPEALS;
    }

    /**
     * Assigns the case to a verifier. A student can never review their own case.
     *
     * @throws DomainException when the case is not pending or the verifier is not valid
     */
    public VerificationCase assignVerifier(int verifierUserId) {
        if (status != CaseStatus.PENDING) {
            throw new DomainException("Only a pending case can be assigned.");
        }
        if (verifierUserId <= 0) {
            throw new DomainException("The verifier must be a valid user.");
        }
        if (verifierUserId == studentId) {
            throw new DomainException("A student cannot review their own case.");
        }
        if (previousVerifierUserId != null && verifierUserId == previousVerifierUserId) {
            throw new DomainException("A verifier cannot review again a case they already resolved.");
        }

        this.verifierUserId = verifierUserId;
        this.status = CaseStatus.ASSIGNED;
        this.assignedAt = Instant.now();
        return this;
    }

    /**
     * Attaches the link to the student's repository or portfolio. A new link replaces the previous one.
     *
     * @throws DomainException when the case is resolved or the link is not a valid http(s) URL
     */
    public VerificationCase attachEvidence(String evidenceUrl) {
        if (status == CaseStatus.RESOLVED) {
            throw new DomainException("A resolved case does not accept evidence.");
        }

        String url = evidenceUrl == null ? "" : evidenceUrl.strip();
        if (url.isEmpty()) {
            throw new DomainException("The evidence link cannot be empty.");
        }
        if (url.length() > MAX_EVIDENCE_URL_LENGTH) {
            throw new DomainException("The evidence link cannot exceed %d characters.".formatted(MAX_EVIDENCE_URL_LENGTH));
        }
        if (!isHttpUrl(url)) {
            throw new DomainException("The evidence link must be a valid http or https URL.");
        }

        this.evidenceUrl = url;
        return this;
    }

    /**
     * Records the verifier's decision and the notes of the rubric.
     *
     * @throws DomainException when the case is already resolved, has no assigned verifier, the decision is
     *                         missing, or the notes are empty or too long
     */
    public VerificationCase resolve(ReviewDecision decision, String rubricNotes) {
        if (status == CaseStatus.RESOLVED) {
            throw new DomainException("The case is already resolved.");
        }
        if (status != CaseStatus.ASSIGNED) {
            throw new DomainException("A case needs an assigned verifier before it can be resolved.");
        }
        if (decision == null) {
            throw new DomainException("The decision is not valid.");
        }

        String notes = rubricNotes == null ? "" : rubricNotes.strip();
        if (notes.isEmpty()) {
            throw new DomainException("The rubric notes are required.");
        }
        if (notes.length() > MAX_RUBRIC_NOTES_LENGTH) {
            throw new DomainException("The rubric notes cannot exceed %d characters.".formatted(MAX_RUBRIC_NOTES_LENGTH));
        }

        this.decision = decision;
        this.rubricNotes = notes;
        this.status = CaseStatus.RESOLVED;
        this.resolvedAt = Instant.now();
        return this;
    }

    /**
     * Reopens a rejected case so another verifier reviews it. The decision and the notes of the first review
     * are cleared, the evidence stays, and the case goes back to pending without a verifier.
     *
     * @throws DomainException when the case is not rejected, or the appeals are used up
     */
    public VerificationCase appeal() {
        if (status != CaseStatus.RESOLVED || decision != ReviewDecision.REJECTED) {
            throw new DomainException("Only a rejected case can be appealed.");
        }
        if (!hasAppealsLeft()) {
            throw new DomainException("The case was already appealed.");
        }

        this.previousVerifierUserId = verifierUserId;
        this.appealCount++;
        this.verifierUserId = null;
        this.decision = null;
        this.rubricNotes = null;
        this.assignedAt = null;
        this.resolvedAt = null;
        this.status = CaseStatus.PENDING;
        return this;
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
}
