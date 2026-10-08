package com.innovify.skillswap.learningpathengine.interfaces.rest;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.AssessmentBlueprint;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.AssessmentBlueprintResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.DeclareGoalResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.LearningPathResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.AssessmentBlueprintResourceFromEntityAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.DeclareGoalCommandFromResourceAssembler;
import com.innovify.skillswap.learningpathengine.interfaces.rest.transform.LearningPathResourceFromEntityAssembler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class LearningPathResourceAssemblersTest {

    @Test
    void command_usesTheAuthenticatedStudent() {
        var command = DeclareGoalCommandFromResourceAssembler.toCommandFromResource(
                new DeclareGoalResource("quiero aprender SQL"), 42);

        assertThat(command.studentId()).isEqualTo(42);
        assertThat(command.rawText()).isEqualTo("quiero aprender SQL");
    }

    @Test
    void command_withAMissingGoal_usesAnEmptyText() {
        assertThat(DeclareGoalCommandFromResourceAssembler
                .toCommandFromResource(new DeclareGoalResource(null), 1).rawText()).isEmpty();
        assertThat(DeclareGoalCommandFromResourceAssembler.toCommandFromResource(null, 1).rawText()).isEmpty();
    }

    @Test
    void path_isMappedWithTheNodesTheirStatesAndTheSkillNames() {
        LearningPath path = TestData.newPath();
        ReflectionTestUtils.setField(path, "id", 11);

        LearningPathResource resource = LearningPathResourceFromEntityAssembler.toResourceFromEntity(path,
                TestData.TAXONOMY::nameOf);

        assertThat(resource.id()).isEqualTo(11);
        assertThat(resource.studentId()).isEqualTo(path.getStudentId());
        assertThat(resource.goal()).isEqualTo(path.getCareerGoal().rawText());
        assertThat(resource.goalSkillTags()).isEqualTo(path.getCareerGoal().mappedSkillTags());
        assertThat(resource.status()).isEqualTo("Active");
        assertThat(resource.nodes()).hasSize(path.getNodes().size());
        assertThat(resource.nodes().get(0).skillTag()).isEqualTo(path.getNodes().get(0).getSkillTag());
        assertThat(resource.nodes().get(0).skillName())
                .isEqualTo(TestData.TAXONOMY.nameOf(path.getNodes().get(0).getSkillTag()));
        assertThat(resource.nodes().get(0).status()).isEqualTo("Available");
        assertThat(resource.nodes().get(resource.nodes().size() - 1).status()).isEqualTo("Locked");
        assertThat(resource.nodes().get(0).order()).isEqualTo(1);
    }

    @Test
    void blueprint_neverExposesTheCorrectAnswers() {
        var blueprint = new AssessmentBlueprint(7, "http-basics", TestData.questions(AssessmentBlueprint.QUESTION_COUNT));
        ReflectionTestUtils.setField(blueprint, "id", 21);

        AssessmentBlueprintResource resource = AssessmentBlueprintResourceFromEntityAssembler
                .toResourceFromEntity(blueprint, TestData.TAXONOMY::nameOf);

        assertThat(resource.id()).isEqualTo(21);
        assertThat(resource.pathNodeId()).isEqualTo(7);
        assertThat(resource.skillTag()).isEqualTo("http-basics");
        assertThat(resource.questions()).hasSize(5);
        assertThat(resource.questions().get(0).question()).isEqualTo(blueprint.getQuestions().get(0).getQuestionString());
        assertThat(resource.questions().get(0).answers()).hasSize(4);
        assertThat(resource.getClass().getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("correct"));
        assertThat(resource.questions().get(0).getClass().getRecordComponents())
                .noneMatch(component -> component.getName().toLowerCase().contains("correct"));
    }
}
