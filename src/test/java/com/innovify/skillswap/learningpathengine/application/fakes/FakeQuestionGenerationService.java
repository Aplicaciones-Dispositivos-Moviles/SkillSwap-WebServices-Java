package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Returns distinct questions on every call, or fails when an exception is set. It can also be told to repeat
 * questions of its previous call, like a provider that ignores the exclusions.
 */
public class FakeQuestionGenerationService implements QuestionGenerationService {

    private final List<String> requests = new ArrayList<>();
    private final List<List<String>> exclusions = new ArrayList<>();
    private RuntimeException exceptionToThrow;
    private int questionCount = AssessmentBlueprint.QUESTION_COUNT;
    private int repeatedCalls;
    private int repeatedQuestions;
    private int calls;
    private List<Question> previousCall = List.of();

    public List<String> requests() {
        return requests;
    }

    /** The questions each call was asked to avoid, in call order. */
    public List<List<String>> exclusions() {
        return exclusions;
    }

    public void failWith(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    public void returnQuestionCount(int count) {
        this.questionCount = count;
    }

    /**
     * The next {@code calls} calls start with the first {@code questions} questions of the call before each of
     * them; 0 and 0 restore the normal behavior.
     */
    public void repeatPreviousQuestions(int calls, int questions) {
        this.repeatedCalls = calls;
        this.repeatedQuestions = questions;
    }

    /** Forgets the calls made, so the next one is a "first call" again. */
    public void reset() {
        requests.clear();
        exclusions.clear();
        calls = 0;
        previousCall = List.of();
        repeatedCalls = 0;
        repeatedQuestions = 0;
        exceptionToThrow = null;
        questionCount = AssessmentBlueprint.QUESTION_COUNT;
    }

    @Override
    public List<Question> generateQuestions(String skillTag, Collection<String> excludedQuestions) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        requests.add(skillTag);
        exclusions.add(List.copyOf(excludedQuestions));
        int offset = calls++ * 10;
        boolean repeat = !previousCall.isEmpty() && repeatedCalls > 0;
        if (repeat) {
            repeatedCalls--;
        }
        List<Question> questions = new ArrayList<>();
        for (int index = 1; index <= questionCount; index++) {
            questions.add(repeat && index <= repeatedQuestions && index <= previousCall.size()
                    ? previousCall.get(index - 1)
                    : TestData.question(offset + index));
        }
        previousCall = List.copyOf(questions);
        return questions;
    }
}
