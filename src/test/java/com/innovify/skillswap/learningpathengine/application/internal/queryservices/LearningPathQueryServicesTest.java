package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeAssessmentBlueprintRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetAssessmentBlueprintByPathNodeIdQuery;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetLearningPathByStudentIdQuery;
import org.junit.jupiter.api.Test;

class LearningPathQueryServicesTest {

    private final FakeAssessmentBlueprintRepository blueprints = new FakeAssessmentBlueprintRepository();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();

    @Test
    void getPath_returnsTheLatestPathOfTheStudent() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
        LearningPath newer = paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
        paths.save(TestData.newUnsavedPath(2, "authentication-jwt"));
        var service = new LearningPathQueryServiceImpl(paths);

        assertThat(service.handle(new GetLearningPathByStudentIdQuery(1))).containsSame(newer);
    }

    @Test
    void getPath_forAStudentWithoutPath_returnsEmpty() {
        var service = new LearningPathQueryServiceImpl(paths);

        assertThat(service.handle(new GetLearningPathByStudentIdQuery(1))).isEmpty();
    }

    @Test
    void getBlueprint_returnsTheLatestBlueprintOfTheNode() {
        blueprints.save(new AssessmentBlueprint(7, "http-basics", TestData.questions(5)));
        AssessmentBlueprint latest = blueprints.save(new AssessmentBlueprint(7, "http-basics", TestData.questions(5)));
        blueprints.save(new AssessmentBlueprint(8, "sql-fundamentals", TestData.questions(5)));
        var service = new AssessmentBlueprintQueryServiceImpl(blueprints);

        assertThat(service.handle(new GetAssessmentBlueprintByPathNodeIdQuery(7))).containsSame(latest);
    }

    @Test
    void getBlueprint_forANodeWithoutBlueprint_returnsEmpty() {
        var service = new AssessmentBlueprintQueryServiceImpl(blueprints);

        assertThat(service.handle(new GetAssessmentBlueprintByPathNodeIdQuery(7))).isEmpty();
    }
}
