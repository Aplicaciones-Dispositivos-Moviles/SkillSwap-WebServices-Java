package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.services.QuestionGenerationService;
import java.util.ArrayList;
import java.util.List;

/** Returns distinct questions on every call, or fails when an exception is set. */
public class FakeQuestionGenerationService implements QuestionGenerationService {

    private final List<String> requests = new ArrayList<>();
    private RuntimeException exceptionToThrow;
    private int questionCount = AssessmentBlueprint.QUESTION_COUNT;
    private int calls;

    public List<String> requests() {
        return requests;
    }

    public void failWith(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    public void returnQuestionCount(int count) {
        this.questionCount = count;
    }

    @Override
    public List<Question> generateQuestions(String skillTag) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        requests.add(skillTag);
        int offset = calls++ * 10;
        List<Question> questions = new ArrayList<>();
        for (int index = 1; index <= questionCount; index++) {
            questions.add(TestData.question(offset + index));
        }
        return questions;
    }
}
