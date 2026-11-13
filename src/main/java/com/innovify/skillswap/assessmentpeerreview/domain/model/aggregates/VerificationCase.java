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
 * Opened when a quiz attempt is not approved, or when a mini-project is delivered, with the type of the work under
 * review. A quiz case belongs to the attempt that opened it and a mini-project case to the delivery, never to both
 * (the mini-project case also carries the link to the repository as its evidence). It is assigned to an enabled verifier, who resolves it with a decision and the notes of the rubric. A student can
 * add evidence while it is open. A rejected case can be appealed once: it reopens and goes to a different
 * verifier, whose decision is final.
 *
 * <p>The review is due by the deadline of the plan the student had when the case was opened; a later change of plan
 * does not move it. When the assigned verifier lets it pass, the breach is recorded once and the case is reassigned to
 * another verifier, who gets the same deadline counted from the reassignment.
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

    /** The attempt that opened a quiz case; null for a mini-project case. Exactly one of the two origins is set. */
    @Column(name = "attempt_id")
    private Integer attemptId;

    /** The delivery that opened a mini-project case; null for a quiz case. */
    @Column(name = "submission_id")
    private Integer submissionId;

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

    /** The deadline the case was opened with; null for the cases opened before it was stored. */
    @Column(name = "review_deadline_amount")
    private Integer reviewDeadlineAmount;

    @Column(name = "review_deadline_unit", length = 20)
    private String reviewDeadlineUnit;

    /** When the current verifier was found past the deadline; null otherwise. */
    @Column(name = "deadline_missed_at")
    private Instant deadlineMissedAt;

    /** How many times the case was reassigned because a verifier missed the deadline. */
    @Column(name = "reassignment_count", nullable = false)
    private int reassignmentCount;

    /** How many times a senior verifier ordered the reevaluation of the case after it was approved. */
    @Column(name = "reevaluation_count", nullable = false)
    private int reevaluationCount;

    /** Required by JPA. */
    protected VerificationCase() {
    }

    /**
     * Opens a quiz case without a review deadline, as the cases opened before the plans set one.
     *
     * @throws DomainException when an id is not valid, the skill is empty or the type is not a quiz
     */
    public VerificationCase(int attemptId, int studentId, int pathNodeId, String skillTag, CaseType caseType) {
        this(attemptId, studentId, pathNodeId, skillTag, caseType, null);
    }

    /**
     * Opens a quiz case, opened by a failed attempt, whose review is due by the deadline of the current plan of the
     * student.
     *
     * @param reviewDeadline the deadline of the plan, or null for none
     * @throws DomainException when an id is not valid, the skill is empty or the type is missing or is not a quiz
     */
    public VerificationCase(int attemptId, int studentId, int pathNodeId, String skillTag, CaseType caseType,
                            ReviewDeadline reviewDeadline) {
        this(attemptId, null, studentId, pathNodeId, skillTag, caseType, null, reviewDeadline);
    }

    /**
     * Opens the case of a delivered mini-project. The link to the repository is the evidence, so the verifier sees
     * it from the start.
     *
     * @param submissionId   the delivery that opened the case
     * @param repositoryUrl  the link to the repository (an http or https URL)
     * @param reviewDeadline the deadline of the current plan of the student, or null for none
     * @throws DomainException when an id is not valid, the skill is empty or the link is not a valid http(s) URL
     */
    public static VerificationCase forMiniProject(int submissionId, int studentId, int pathNodeId, String skillTag,
                                                  String repositoryUrl, ReviewDeadline reviewDeadline) {
        return new VerificationCase(null, submissionId, studentId, pathNodeId, skillTag, CaseType.MINI_PROJECT,
                validEvidenceUrl(repositoryUrl), reviewDeadline);
    }

    private VerificationCase(Integer attemptId, Integer submissionId, int studentId, int pathNodeId, String skillTag,
                             CaseType caseType, String evidenceUrl, ReviewDeadline reviewDeadline) {
        if (caseType == null) {
            throw new DomainException("The case type is required.");
        }
        if (attemptId != null) {
            if (attemptId <= 0) {
                throw new DomainException("The case must belong to a valid attempt.");
            }
            if (caseType != CaseType.QUIZ) {
                throw new DomainException("A case opened by an attempt must be a quiz.");
            }
        }
        if (submissionId != null) {
            if (submissionId <= 0) {
                throw new DomainException("The case must belong to a valid delivery.");
            }
            if (caseType != CaseType.MINI_PROJECT) {
                throw new DomainException("A case opened by a delivery must be a mini-project.");
            }
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

        this.attemptId = attemptId;
        this.submissionId = submissionId;
        this.studentId = studentId;
        this.pathNodeId = pathNodeId;
        this.skillTag = skillTag.strip();
        this.caseType = caseType;
        this.evidenceUrl = evidenceUrl;
        this.status = CaseStatus.PENDING;
        this.openedAt = Instant.now();
        this.reviewDueAt = reviewDeadline == null ? null : reviewDeadline.dueFrom(openedAt);
        if (reviewDeadline != null) {
            this.reviewDeadlineAmount = reviewDeadline.amount();
            this.reviewDeadlineUnit = reviewDeadline.unit().value();
        }
    }

    public Integer getId() {
        return id;
    }

    /** The attempt that opened a quiz case; null for a mini-project case. */
    public Integer getAttemptId() {
        return attemptId;
    }

    /** The delivery that opened a mini-project case; null for a quiz case. */
    public Integer getSubmissionId() {
        return submissionId;
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

    /** The deadline the case was opened with; null for the cases opened before it was stored. */
    public ReviewDeadline getReviewDeadline() {
        if (reviewDeadlineAmount == null || reviewDeadlineUnit == null) {
            return null;
        }
        return new ReviewDeadline(reviewDeadlineAmount, ReviewDeadline.Unit.fromValue(reviewDeadlineUnit));
    }

    /** When the current verifier was found past the deadline; null while they are within it. */
    public Instant getDeadlineMissedAt() {
        return deadlineMissedAt;
    }

    public int getReassignmentCount() {
        return reassignmentCount;
    }

    public int getReevaluationCount() {
        return reevaluationCount;
    }

    /** Whether the case is assigned and its review was due before that moment. */
    public boolean isOverdue(Instant now) {
        return status == CaseStatus.ASSIGNED && reviewDueAt != null && now != null && !now.isBefore(reviewDueAt);
    }

    /**
     * Records that the assigned verifier let the deadline pass. It is recorded once per assignment, however many times
     * the case is checked.
     *
     * @return true when the breach was recorded now; false when it already was
     * @throws DomainException when the case is not overdue
     */
    public boolean recordMissedDeadline(Instant now) {
        if (!isOverdue(now)) {
            throw new DomainException("Only an assigned case past its deadline can miss it.");
        }
        if (deadlineMissedAt != null) {
            return false;
        }
        this.deadlineMissedAt = now;
        return true;
    }

    /**
     * Gives an overdue case to another verifier, with the deadline it was opened with counted from now.
     *
     * @param deadline the deadline to apply when the case has none stored (the one of the current plan)
     * @throws DomainException when the missed deadline was not recorded, or the verifier is not valid, is the student,
     *                         the verifier who missed it or the one who resolved it before an appeal
     */
    public VerificationCase reassignAfterMissedDeadline(int newVerifierUserId, ReviewDeadline deadline, Instant now) {
        if (status != CaseStatus.ASSIGNED || deadlineMissedAt == null) {
            throw new DomainException("Only an assigned case whose deadline was missed can be reassigned.");
        }
        if (newVerifierUserId <= 0) {
            throw new DomainException("The verifier must be a valid user.");
        }
        if (newVerifierUserId == studentId) {
            throw new DomainException("A student cannot review their own case.");
        }
        if (isAssignedTo(newVerifierUserId)) {
            throw new DomainException("The case must go to a verifier other than the one who missed the deadline.");
        }
        if (previousVerifierUserId != null && newVerifierUserId == previousVerifierUserId) {
            throw new DomainException("A verifier cannot review again a case they already resolved.");
        }
        ReviewDeadline applied = getReviewDeadline() != null ? getReviewDeadline() : deadline;
        if (applied == null) {
            throw new DomainException("The deadline of the reassigned case is required.");
        }

        this.verifierUserId = newVerifierUserId;
        this.assignedAt = now;
        this.reviewDueAt = applied.dueFrom(now);
        this.deadlineMissedAt = null;
        this.reassignmentCount++;
        return this;
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
     * confirmed, a case that is not resolved, or a case reopened by a reevaluation (the verifier who approved it
     * before is not overturned by a new decision).
     */
    public Integer overturnedVerifierUserId() {
        return status == CaseStatus.RESOLVED && decision == ReviewDecision.APPROVED && reevaluationCount == 0
                ? previousVerifierUserId
                : null;
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
     * Takes the case back from a verifier who was suspended: it waits again for a verifier. It is not a missed
     * deadline, so nothing is counted against the verifier, and the new verifier gets the whole deadline from now.
     *
     * @throws DomainException when the case is not assigned
     */
    public VerificationCase releaseFromSuspendedVerifier(Instant now) {
        if (status != CaseStatus.ASSIGNED) {
            throw new DomainException("Only an assigned case can be taken back from its verifier.");
        }
        if (now == null) {
            throw new DomainException("The moment of the release is required.");
        }

        ReviewDeadline deadline = getReviewDeadline();
        if (deadline != null) {
            this.reviewDueAt = deadline.dueFrom(now);
        }
        this.verifierUserId = null;
        this.assignedAt = null;
        this.deadlineMissedAt = null;
        this.status = CaseStatus.PENDING;
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

        this.evidenceUrl = validEvidenceUrl(evidenceUrl);
        return this;
    }

    /** The link without spaces, when it is a valid http(s) URL of at most 500 characters. */
    private static String validEvidenceUrl(String evidenceUrl) {
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
        return url;
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

    /** True when the case is resolved with an approval, which a senior verifier can order to evaluate again. */
    public boolean canBeReevaluated() {
        return status == CaseStatus.RESOLVED && decision == ReviewDecision.APPROVED;
    }

    /**
     * Reopens an approved case so another verifier reviews it. The decision and the notes of the first review are
     * cleared, the evidence stays, and the case goes back to pending without a verifier. The verifier who approved it
     * cannot review it again, and the review deadline is counted again from now. It is not an appeal: it does not use
     * the appeal of the student.
     *
     * @param reviewDeadline the deadline to apply to the new review, or null for none
     * @throws DomainException when the case is not approved
     */
    public VerificationCase reopenForReevaluation(ReviewDeadline reviewDeadline) {
        if (!canBeReevaluated()) {
            throw new DomainException("Only an approved case can be reevaluated.");
        }

        this.previousVerifierUserId = verifierUserId;
        this.reevaluationCount++;
        this.verifierUserId = null;
        this.decision = null;
        this.rubricNotes = null;
        this.assignedAt = null;
        this.resolvedAt = null;
        this.deadlineMissedAt = null;
        this.reviewDueAt = reviewDeadline == null ? null : reviewDeadline.dueFrom(Instant.now());
        this.reviewDeadlineAmount = reviewDeadline == null ? null : reviewDeadline.amount();
        this.reviewDeadlineUnit = reviewDeadline == null ? null : reviewDeadline.unit().value();
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
