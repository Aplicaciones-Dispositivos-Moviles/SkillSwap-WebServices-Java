package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

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
 * only the delivery itself is valid or not. The AI preliminary evaluation and the case that follow are not part of
 * this context's delivery: they react to the {@code MiniProjectSubmitted} event.
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
}
