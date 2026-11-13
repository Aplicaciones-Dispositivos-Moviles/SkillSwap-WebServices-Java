package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.entities.RubricCriterion;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.MiniProjectDraft;
import com.innovify.skillswap.learningpathengine.domain.services.MiniProjectGenerationService;
import java.util.ArrayList;
import java.util.List;

/** Returns a different valid mini-project on every call, or fails when an exception is set. */
public class FakeMiniProjectGenerationService implements MiniProjectGenerationService {

    private final List<String> requests = new ArrayList<>();
    private RuntimeException exceptionToThrow;
    private MiniProjectDraft draftToReturn;

    public List<String> requests() {
        return requests;
    }

    public void failWith(RuntimeException exception) {
        this.exceptionToThrow = exception;
    }

    /** The draft returned by every following call, to test output that breaks the contract; null restores the default. */
    public void returnDraft(MiniProjectDraft draft) {
        this.draftToReturn = draft;
    }

    /** Forgets the calls made and the configured behavior. */
    public void reset() {
        requests.clear();
        exceptionToThrow = null;
        draftToReturn = null;
    }

    @Override
    public MiniProjectDraft generateMiniProject(String skillTag) {
        if (exceptionToThrow != null) {
            throw exceptionToThrow;
        }
        requests.add(skillTag);
        if (draftToReturn != null) {
            return draftToReturn;
        }
        List<RubricCriterion> rubric = TestData.rubric();
        return new MiniProjectDraft("Mini-project " + requests.size() + " of " + skillTag,
                "Build a small project that demonstrates " + skillTag + ".", rubric);
    }
}
