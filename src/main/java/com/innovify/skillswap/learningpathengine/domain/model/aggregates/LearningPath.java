package com.innovify.skillswap.learningpathengine.domain.model.aggregates;

import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The personalized learning path of a student: an ordered sequence of {@link PathNode}s to complete toward the
 * declared {@link CareerGoal}. A node is locked until all its prerequisites are completed; a linked
 * certificate is supporting evidence only and never completes a node.
 *
 * <p>The goal and the status are mapped to their columns by the auto-applied attribute converters of the
 * infrastructure layer.
 */
@Entity
@Table(name = "learning_paths")
public class LearningPath {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "student_id", nullable = false)
    private int studentId;

    @Column(name = "career_goal", nullable = false, columnDefinition = "jsonb")
    private CareerGoal careerGoal;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "learning_path_id", nullable = false)
    @OrderBy("nodeOrder ASC")
    private List<PathNode> nodes = new ArrayList<>();

    @Column(name = "status", nullable = false, length = 20)
    private PathStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA. */
    protected LearningPath() {
    }

    /**
     * Creates an active path from the nodes produced by the learning path builder.
     *
     * @throws DomainException when the student is not valid, there are no nodes, orders or skills repeat, a
     *                         prerequisite is outside the path, or a node is already completed
     */
    public LearningPath(int studentId, CareerGoal careerGoal, Collection<PathNode> nodes) {
        if (studentId <= 0) {
            throw new DomainException("The path must belong to a valid student.");
        }

        List<PathNode> list = (nodes == null ? List.<PathNode>of() : nodes).stream()
                .sorted(Comparator.comparingInt(PathNode::getOrder))
                .toList();
        if (list.isEmpty()) {
            throw new DomainException("A learning path needs at least one node.");
        }
        if (list.stream().map(PathNode::getOrder).distinct().count() != list.size()) {
            throw new DomainException("The node orders of a path must be unique.");
        }

        Set<String> tags = new HashSet<>();
        list.forEach(node -> tags.add(node.getSkillTag()));
        if (tags.size() != list.size()) {
            throw new DomainException("A skill can appear only once in a path.");
        }
        if (list.stream().anyMatch(node -> !tags.containsAll(node.getPrerequisiteSkillTags()))) {
            throw new DomainException("A node references a prerequisite that is not part of the path.");
        }
        if (list.stream().anyMatch(node -> node.getStatus() == NodeStatus.COMPLETED)) {
            throw new DomainException("A new learning path cannot contain completed nodes.");
        }

        this.studentId = studentId;
        this.careerGoal = careerGoal;
        this.nodes.addAll(list);
        this.status = PathStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public Integer getId() {
        return id;
    }

    public int getStudentId() {
        return studentId;
    }

    public CareerGoal getCareerGoal() {
        return careerGoal;
    }

    /** The nodes in path order. */
    public List<PathNode> getNodes() {
        return nodes.stream().sorted(Comparator.comparingInt(PathNode::getOrder)).toList();
    }

    public PathStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** The node with this id, if it belongs to the path. */
    public Optional<PathNode> getNode(int nodeId) {
        return nodes.stream().filter(node -> node.getId() != null && node.getId() == nodeId).findFirst();
    }

    /**
     * Completes an available node and unlocks every node whose prerequisites are now all completed. When the
     * last node is completed, the whole path is completed.
     *
     * @throws DomainException when the node is not part of the path, is locked or is already completed
     */
    public LearningPath completeNode(int nodeId) {
        PathNode node = requireNode(nodeId);
        if (node.getStatus() == NodeStatus.LOCKED) {
            throw new DomainException(
                    "The node '%s' is locked: complete its prerequisites first.".formatted(node.getSkillTag()));
        }
        if (node.getStatus() == NodeStatus.COMPLETED) {
            throw new DomainException("The node '%s' is already completed.".formatted(node.getSkillTag()));
        }

        node.complete();

        Set<String> completed = completedSkillTags();
        nodes.stream()
                .filter(n -> n.getStatus() == NodeStatus.LOCKED && completed.containsAll(n.getPrerequisiteSkillTags()))
                .forEach(PathNode::unlock);

        if (nodes.stream().allMatch(n -> n.getStatus() == NodeStatus.COMPLETED)) {
            status = PathStatus.COMPLETED;
        }
        updatedAt = Instant.now();
        return this;
    }

    /**
     * Links a certificate to a node as supporting evidence. The first certificate stays linked, and a
     * completed node does not accept one.
     *
     * @return whether the certificate was linked
     * @throws DomainException when the certificate id is not valid or the node is not part of the path
     */
    public boolean linkCertificate(int nodeId, int certificateId) {
        requireValidCertificate(certificateId);
        return tryLink(requireNode(nodeId), certificateId);
    }

    /**
     * Same as {@link #linkCertificate(int, int)}, for the node of a skill. Works on nodes that are not saved
     * yet.
     *
     * @return whether the certificate was linked; false when the skill is not part of the path
     */
    public boolean linkCertificateToSkill(String skillTag, int certificateId) {
        requireValidCertificate(certificateId);
        return nodes.stream()
                .filter(node -> node.getSkillTag().equals(skillTag))
                .findFirst()
                .map(node -> tryLink(node, certificateId))
                .orElse(false);
    }

    /** The prerequisites of a node that are not completed yet. */
    public List<String> pendingPrerequisitesOf(int nodeId) {
        PathNode node = requireNode(nodeId);
        Set<String> completed = completedSkillTags();
        return node.getPrerequisiteSkillTags().stream().filter(tag -> !completed.contains(tag)).toList();
    }

    /**
     * Points an available node to its latest assessment. A new one replaces the previous.
     *
     * @throws DomainException when the blueprint id is not valid or the node is not available
     */
    public LearningPath attachBlueprint(int nodeId, int blueprintId) {
        if (blueprintId <= 0) {
            throw new DomainException("The blueprint id is not valid.");
        }

        PathNode node = requireNode(nodeId);
        if (node.getStatus() != NodeStatus.AVAILABLE) {
            throw new DomainException("An assessment can only be attached to an available node.");
        }

        node.attachBlueprint(blueprintId);
        updatedAt = Instant.now();
        return this;
    }

    private Set<String> completedSkillTags() {
        Set<String> completed = new HashSet<>();
        nodes.stream().filter(n -> n.getStatus() == NodeStatus.COMPLETED).forEach(n -> completed.add(n.getSkillTag()));
        return completed;
    }

    private boolean tryLink(PathNode node, int certificateId) {
        if (node.getStatus() == NodeStatus.COMPLETED || node.getLinkedCertificateId() != null) {
            return false;
        }
        node.linkCertificate(certificateId);
        updatedAt = Instant.now();
        return true;
    }

    private static void requireValidCertificate(int certificateId) {
        if (certificateId <= 0) {
            throw new DomainException("The certificate id is not valid.");
        }
    }

    private PathNode requireNode(int nodeId) {
        return getNode(nodeId).orElseThrow(
                () -> new DomainException("The node %d does not belong to this path.".formatted(nodeId)));
    }
}
