package com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates;

import com.innovify.skillswap.assessmentpeerreview.domain.model.valueobjects.Score;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A student's attempt at the assessment of a node. The score is always computed on the server, against the
 * correct answers of the blueprint, so the client cannot tamper with it.
 */
public class AssessmentAttempt {

    public static final int ANSWER_OPTION_COUNT = 4;
    public static final int PASSING_SCORE = 4;

    private Integer id;
    private int blueprintId;
    private int studentId;
    private List<Integer> selectedAnswers;
    private Score score;
    private boolean passed;
    private Instant completedAt;

    /** Required by the persistence layer. */
    protected AssessmentAttempt() {
    }

    /**
     * @throws DomainException when the ids are not valid, there are fewer than {@value #PASSING_SCORE} questions,
     *                         there is not exactly one answer per question, or an answer is not a valid option
     */
    public AssessmentAttempt(int blueprintId, int studentId, List<Integer> selectedAnswers,
                             List<Integer> correctAnswers) {
        if (blueprintId <= 0) {
            throw new DomainException("The attempt must belong to a valid blueprint.");
        }
        if (studentId <= 0) {
            throw new DomainException("The attempt must belong to a valid student.");
        }
        if (correctAnswers == null || correctAnswers.size() < PASSING_SCORE) {
            throw new DomainException("An assessment needs at least %d questions.".formatted(PASSING_SCORE));
        }
        if (selectedAnswers == null || selectedAnswers.size() != correctAnswers.size()) {
            throw new DomainException("There must be exactly one answer per question.");
        }
        if (selectedAnswers.stream().anyMatch(answer -> answer == null || answer < 0 || answer >= ANSWER_OPTION_COUNT)) {
            throw new DomainException("Each answer must be an index between 0 and %d.".formatted(ANSWER_OPTION_COUNT - 1));
        }

        int hits = 0;
        for (int index = 0; index < selectedAnswers.size(); index++) {
            if (selectedAnswers.get(index).equals(correctAnswers.get(index))) {
                hits++;
            }
        }

        this.blueprintId = blueprintId;
        this.studentId = studentId;
        this.selectedAnswers = List.copyOf(selectedAnswers);
        this.score = new Score(hits, correctAnswers.size());
        this.passed = hits >= PASSING_SCORE;
        this.completedAt = Instant.now();
    }

    public Integer getId() {
        return id;
    }

    public int getBlueprintId() {
        return blueprintId;
    }

    public int getStudentId() {
        return studentId;
    }

    public List<Integer> getSelectedAnswers() {
        return selectedAnswers;
    }

    public Score getScore() {
        return score;
    }

    public boolean isPassed() {
        return passed;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    /**
     * The positions (starting at 0) of the questions that were answered incorrectly.
     *
     * @throws DomainException when the number of correct answers does not match the attempt
     */
    public List<Integer> incorrectQuestionIndexes(List<Integer> correctAnswers) {
        if (correctAnswers == null || correctAnswers.size() != selectedAnswers.size()) {
            throw new DomainException("The correct answers do not match the attempt.");
        }

        List<Integer> incorrect = new ArrayList<>();
        for (int index = 0; index < selectedAnswers.size(); index++) {
            if (!selectedAnswers.get(index).equals(correctAnswers.get(index))) {
                incorrect.add(index);
            }
        }
        return List.copyOf(incorrect);
    }
}
