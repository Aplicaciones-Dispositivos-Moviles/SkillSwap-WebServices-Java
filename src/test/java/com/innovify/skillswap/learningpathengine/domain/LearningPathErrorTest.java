package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.domain.model.LearningPathError;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.PathStatus;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The codes are part of the API contract (title of the error responses): they must match the C# names. */
class LearningPathErrorTest {

    @Test
    void codes_matchTheNamesOfTheCSharpApi() {
        assertThat(Arrays.stream(LearningPathError.values()).map(ErrorCodes::of)).containsExactly(
                "None", "InvalidGoal", "GoalNotInterpretable", "GoalAlreadyAchieved", "ActivePathAlreadyExists",
                "PathNotFound", "NotPathOwner", "NodeNotFound", "NodeLocked", "NodeAlreadyCompleted",
                "QuestionGenerationFailed", "OperationCancelled", "DatabaseError", "InternalServerError");
    }

    @Test
    void nodeStatus_usesTheStoredValues() {
        assertThat(Arrays.stream(NodeStatus.values()).map(NodeStatus::value))
                .containsExactly("Locked", "Available", "Completed");
        assertThat(NodeStatus.fromValue("available")).isEqualTo(NodeStatus.AVAILABLE);
        assertThatThrownBy(() -> NodeStatus.fromValue("Other")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pathStatus_usesTheStoredValues() {
        assertThat(Arrays.stream(PathStatus.values()).map(PathStatus::value)).containsExactly("Active", "Completed");
        assertThat(PathStatus.fromValue("COMPLETED")).isEqualTo(PathStatus.COMPLETED);
        assertThatThrownBy(() -> PathStatus.fromValue("Other")).isInstanceOf(IllegalArgumentException.class);
    }
}
