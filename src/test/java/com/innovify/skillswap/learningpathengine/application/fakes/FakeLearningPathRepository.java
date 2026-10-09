package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns path and node ids like the database does. */
public class FakeLearningPathRepository implements LearningPathRepository {

    private final List<LearningPath> paths = new ArrayList<>();
    private int nextId = 1;
    private int nextNodeId = 1;
    private int saveCalls;
    private final List<Integer> lockedStudents = new ArrayList<>();
    private RuntimeException saveFailure;

    /** The students whose paths were locked, in order. */
    public List<Integer> lockedStudents() {
        return lockedStudents;
    }

    public List<LearningPath> paths() {
        return paths;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public LearningPath save(LearningPath path) {
        saveCalls++;
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (path.getId() == null) {
            ReflectionTestUtils.setField(path, "id", nextId++);
        }
        for (PathNode node : path.getNodes()) {
            if (node.getId() == null) {
                ReflectionTestUtils.setField(node, "id", nextNodeId++);
            }
        }
        if (!paths.contains(path)) {
            paths.add(path);
        }
        return path;
    }

    @Override
    public Optional<LearningPath> findById(int id) {
        return paths.stream().filter(p -> p.getId() == id).findFirst();
    }

    @Override
    public List<LearningPath> findByStudentId(int studentId) {
        return paths.stream().filter(p -> p.getStudentId() == studentId)
                .sorted(Comparator.comparing(LearningPath::getId).reversed())
                .toList();
    }

    @Override
    public int countByStudentId(int studentId) {
        return (int) paths.stream().filter(p -> p.getStudentId() == studentId).count();
    }

    @Override
    public int countActiveByStudentId(int studentId) {
        return (int) paths.stream()
                .filter(p -> p.getStudentId() == studentId && p.getStatus() == PathStatus.ACTIVE)
                .count();
    }

    @Override
    public void lockStudentPaths(int studentId) {
        lockedStudents.add(studentId);
    }

    @Override
    public Optional<LearningPath> findLatestByStudentId(int studentId) {
        return paths.stream().filter(p -> p.getStudentId() == studentId)
                .max(Comparator.comparing(LearningPath::getId));
    }

    @Override
    public Optional<LearningPath> findByNodeId(int nodeId) {
        return paths.stream().filter(p -> p.getNode(nodeId).isPresent()).findFirst();
    }

    @Override
    public Collection<String> findCompletedSkillTagsByStudentId(int studentId) {
        return paths.stream()
                .filter(p -> p.getStudentId() == studentId)
                .flatMap(p -> p.getNodes().stream())
                .filter(n -> n.getStatus() == NodeStatus.COMPLETED)
                .map(PathNode::getSkillTag)
                .distinct()
                .toList();
    }
}
