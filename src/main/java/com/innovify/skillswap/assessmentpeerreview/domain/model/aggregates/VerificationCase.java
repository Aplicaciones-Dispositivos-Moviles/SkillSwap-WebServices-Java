package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.ReviewDecision;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Locale;

/**
 * Opened when an attempt is not approved. It is assigned to an enabled verifier, who resolves it with a
 * decision and the notes of the rubric. A student can add evidence while it is open.
 */
public class VerificationCase {

    public static final int MAX_EVIDENCE_URL_LENGTH = 500;
    public static final int MAX_RUBRIC_NOTES_LENGTH = 2000;

    private Integer id;
    private int attemptId;
    private int studentId;
    private Integer verifierUserId;
    private int pathNodeId;
    private String skillTag;
    private CaseStatus status;
    private ReviewDecision decision;
    private String rubricNotes;
    private String evidenceUrl;
    private Instant openedAt;
    private Instant assignedAt;
    private Instant resolvedAt;

    /** Required by the persistence layer. */
    protected VerificationCase() {
    }

    /** @throws DomainException when an id is not valid or the skill is empty */
    public VerificationCase(int attemptId, int studentId, int pathNodeId, String skillTag) {
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

        this.attemptId = attemptId;
        this.studentId = studentId;
        this.pathNodeId = pathNodeId;
        this.skillTag = skillTag.strip();
        this.status = CaseStatus.PENDING;
        this.openedAt = Instant.now();
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

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public boolean isOpen() {
        return status != CaseStatus.RESOLVED;
    }

    public boolean isAssignedTo(int userId) {
        return verifierUserId != null && verifierUserId == userId;
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
