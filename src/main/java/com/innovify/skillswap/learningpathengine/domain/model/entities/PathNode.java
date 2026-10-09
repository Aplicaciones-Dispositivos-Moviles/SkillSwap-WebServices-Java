package com.innovify.skillswap.learningpathengine.domain.model.entities;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * One step of a learning path: a skill the student must demonstrate. It belongs to its
 * {@link com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath}, which is the only one
 * that should call the methods that change it.
 *
 * <p>The direct prerequisites are stored as a comma-separated list of kebab-case skill tags.
 */
@Entity
@Table(name = "path_nodes")
public class PathNode {

    private static final String SEPARATOR = ",";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "skill_tag", nullable = false, length = 100)
    private String skillTag;

    @Column(name = "node_order", nullable = false)
    private int nodeOrder;

    @Column(name = "status", nullable = false, length = 20)
    private NodeStatus status;

    @Column(name = "prerequisites", nullable = false, length = 1000)
    private String prerequisites;

    @Column(name = "linked_certificate_id")
    private Integer linkedCertificateId;

    @Column(name = "assessment_blueprint_id")
    private Integer assessmentBlueprintId;

    @Column(name = "completed_by_certificate", nullable = false)
    private boolean completedByCertificate;

    /** Required by JPA. */
    protected PathNode() {
    }

    /**
     * Creates a node. It starts {@link NodeStatus#AVAILABLE} without prerequisites, and
     * {@link NodeStatus#LOCKED} otherwise.
     *
     * @param order 1-based position in the path
     * @throws DomainException when the skill is blank, the order is not positive or the skill requires itself
     */
    public PathNode(String skillTag, int order, Collection<String> prerequisiteSkillTags) {
        if (skillTag == null || skillTag.isBlank()) {
            throw new DomainException("The skill tag cannot be empty.");
        }
        if (order <= 0) {
            throw new DomainException("The node order must be positive.");
        }

        this.skillTag = skillTag.strip();
        this.nodeOrder = order;
        List<String> tags = (prerequisiteSkillTags == null ? List.<String>of() : prerequisiteSkillTags).stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .distinct()
                .sorted()
                .toList();
        if (tags.contains(this.skillTag)) {
            throw new DomainException("A skill cannot be a prerequisite of itself.");
        }
        this.prerequisites = String.join(SEPARATOR, tags);
        this.status = tags.isEmpty() ? NodeStatus.AVAILABLE : NodeStatus.LOCKED;
    }

    public Integer getId() {
        return id;
    }

    public String getSkillTag() {
        return skillTag;
    }

    public int getOrder() {
        return nodeOrder;
    }

    public NodeStatus getStatus() {
        return status;
    }

    /** Direct prerequisites that are part of the same path, sorted. */
    public List<String> getPrerequisiteSkillTags() {
        return prerequisites == null || prerequisites.isBlank()
                ? List.of()
                : Arrays.stream(prerequisites.split(SEPARATOR)).map(String::strip).toList();
    }

    /**
     * The certificate linked to this skill, if any. Usually it is supporting evidence that does not complete the
     * node; only a certificate validated by a verifier completes it ({@link #isCompletedByCertificate()}).
     */
    public Integer getLinkedCertificateId() {
        return linkedCertificateId;
    }

    /** Whether the node was completed because a validated certificate already covers its skill (US09). */
    public boolean isCompletedByCertificate() {
        return completedByCertificate;
    }

    /** The latest assessment generated for this node, if any. */
    public Integer getAssessmentBlueprintId() {
        return assessmentBlueprintId;
    }

    /** Called by the learning path when the node's prerequisites are completed. */
    public void unlock() {
        if (status == NodeStatus.LOCKED) {
            status = NodeStatus.AVAILABLE;
        }
    }

    /** Called by the learning path. */
    public void complete() {
        status = NodeStatus.COMPLETED;
    }

    /** Called by the learning path. */
    public void linkCertificate(int certificateId) {
        this.linkedCertificateId = certificateId;
    }

    /** Called by the learning path: a validated certificate covers the skill, so it counts as demonstrated. */
    public void completeWithCertificate(int certificateId) {
        this.status = NodeStatus.COMPLETED;
        this.linkedCertificateId = certificateId;
        this.completedByCertificate = true;
    }

    /** Called by the learning path. */
    public void attachBlueprint(int blueprintId) {
        this.assessmentBlueprintId = blueprintId;
    }
}
