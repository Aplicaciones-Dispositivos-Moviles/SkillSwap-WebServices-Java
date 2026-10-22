package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerificationCase;
import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.CaseStatus;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerificationCaseRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.test.util.ReflectionTestUtils;

/** In-memory repository that assigns ids like the database does. */
public class FakeVerificationCaseRepository implements VerificationCaseRepository {

    private final List<VerificationCase> cases = new ArrayList<>();
    private int nextId = 1;
    private final List<Integer> lockedStudents = new ArrayList<>();
    private final List<Integer> lockedCases = new ArrayList<>();
    private RuntimeException saveFailure;

    /** The students whose escalations were locked, in order. */
    public List<Integer> lockedStudents() {
        return lockedStudents;
    }

    /** The cases read with a lock, in order. */
    public List<Integer> lockedCases() {
        return lockedCases;
    }

    public List<VerificationCase> cases() {
        return cases;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public VerificationCase save(VerificationCase verificationCase) {
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (verificationCase.getId() == null) {
            ReflectionTestUtils.setField(verificationCase, "id", nextId++);
        }
        if (!cases.contains(verificationCase)) {
            cases.add(verificationCase);
        }
        return verificationCase;
    }

    @Override
    public Optional<VerificationCase> findById(int id) {
        return cases.stream().filter(c -> c.getId() == id).findFirst();
    }

    @Override
    public Optional<VerificationCase> findByIdForUpdate(int id) {
        lockedCases.add(id);
        return findById(id);
    }

    @Override
    public List<Integer> findOverdueAssignedIds(Instant now) {
        return cases.stream()
                .filter(c -> c.isOverdue(now))
                .sorted(Comparator.comparing(VerificationCase::getReviewDueAt).thenComparing(VerificationCase::getId))
                .map(VerificationCase::getId)
                .toList();
    }

    @Override
    public List<Integer> findAssignedIdsByVerifierUserId(int verifierUserId) {
        return cases.stream()
                .filter(c -> c.getStatus() == CaseStatus.ASSIGNED && c.isAssignedTo(verifierUserId))
                .map(VerificationCase::getId)
                .sorted()
                .toList();
    }

    @Override
    public List<VerificationCase> findByVerifierUserId(int verifierUserId) {
        return cases.stream()
                .filter(c -> c.isAssignedTo(verifierUserId))
                .sorted(Comparator.comparing(VerificationCase::getId).reversed())
                .toList();
    }

    @Override
    public Optional<VerificationCase> findBySubmissionId(int submissionId) {
        return cases.stream().filter(c -> c.getSubmissionId() != null && c.getSubmissionId() == submissionId)
                .findFirst();
    }

    @Override
    public Optional<VerificationCase> findOpenByStudentAndNode(int studentId, int pathNodeId) {
        return cases.stream()
                .filter(c -> c.getStudentId() == studentId && c.getPathNodeId() == pathNodeId && c.isOpen())
                .findFirst();
    }

    @Override
    public List<VerificationCase> findPendingBySkillTag(String skillTag) {
        return cases.stream()
                .filter(c -> c.getStatus() == CaseStatus.PENDING && c.getSkillTag().equals(skillTag))
                .sorted(Comparator.comparing(VerificationCase::getId))
                .toList();
    }

    @Override
    public int countOpenedByStudentSince(int studentId, Instant since) {
        return (int) cases.stream()
                .filter(c -> c.getStudentId() == studentId && !c.getOpenedAt().isBefore(since))
                .count();
    }

    @Override
    public void lockStudentEscalations(int studentId) {
        lockedStudents.add(studentId);
    }

    @Override
    public Map<Integer, Integer> countOpenByVerifierUserIds(Collection<Integer> verifierUserIds) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Integer userId : verifierUserIds) {
            int open = (int) cases.stream().filter(c -> c.isAssignedTo(userId) && c.isOpen()).count();
            if (open > 0) {
                counts.put(userId, open);
            }
        }
        return counts;
    }

    @Override
    public Map<Integer, Double> averageResolutionSecondsByVerifier() {
        Map<Integer, java.util.List<Double>> seconds = new HashMap<>();
        for (VerificationCase c : cases) {
            if (c.getStatus() == CaseStatus.RESOLVED && c.getVerifierUserId() != null && c.getAssignedAt() != null
                    && c.getResolvedAt() != null) {
                seconds.computeIfAbsent(c.getVerifierUserId(), id -> new ArrayList<>())
                        .add((double) Duration.between(c.getAssignedAt(), c.getResolvedAt()).toSeconds());
            }
        }
        Map<Integer, Double> averages = new HashMap<>();
        seconds.forEach((id, values) -> averages.put(id, values.stream().mapToDouble(Double::doubleValue)
                .average().orElse(0)));
        return averages;
    }
}
