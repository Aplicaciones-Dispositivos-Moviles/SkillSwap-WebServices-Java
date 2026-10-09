package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.learningpathengine.application.acl.BlueprintQuestionView;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FakeLearningPathContextFacade implements LearningPathContextFacade {

    /** The correct answers of the blueprints built by {@link #addBlueprint()}. */
    public static final List<Integer> CORRECT = List.of(1, 2, 3, 0, 1);

    private final Map<Integer, BlueprintView> blueprints = new HashMap<>();
    private final Set<String> completedSkills = new HashSet<>();
    private final List<Integer> completedNodes = new ArrayList<>();
    private NodeCompletionOutcome nextOutcome = NodeCompletionOutcome.COMPLETED;

    public List<Integer> completedNodes() {
        return completedNodes;
    }

    /** What {@link #completeNode} reports; the node is recorded only when it is COMPLETED. */
    public void setNextOutcome(NodeCompletionOutcome nextOutcome) {
        this.nextOutcome = nextOutcome;
    }

    public void addCompletedSkill(int studentId, String skillTag) {
        completedSkills.add(studentId + ":" + skillTag);
    }

    /** The blueprint 1 of node 10, owned by student 1, about "http-basics". */
    public BlueprintView addBlueprint() {
        return addBlueprint(1, 10, 1, "http-basics", true, true);
    }

    public BlueprintView addBlueprint(boolean isLatest, boolean nodeIsAvailable) {
        return addBlueprint(1, 10, 1, "http-basics", isLatest, nodeIsAvailable);
    }

    public BlueprintView addBlueprint(int blueprintId, int pathNodeId, int studentId, String skillTag,
                                      boolean isLatest, boolean nodeIsAvailable) {
        List<BlueprintQuestionView> questions = new ArrayList<>();
        for (int index = 0; index < CORRECT.size(); index++) {
            int number = index + 1;
            questions.add(new BlueprintQuestionView("Question " + number + "?",
                    List.of("a" + number, "b" + number, "c" + number, "d" + number), CORRECT.get(index)));
        }
        BlueprintView view = new BlueprintView(blueprintId, pathNodeId, studentId, skillTag, isLatest,
                nodeIsAvailable, questions);
        blueprints.put(blueprintId, view);
        return view;
    }

    @Override
    public Optional<BlueprintView> getBlueprint(int blueprintId) {
        return Optional.ofNullable(blueprints.get(blueprintId));
    }

    @Override
    public NodeCompletionOutcome completeNode(int pathNodeId) {
        if (nextOutcome == NodeCompletionOutcome.COMPLETED) {
            completedNodes.add(pathNodeId);
        }
        return nextOutcome;
    }

    @Override
    public boolean hasCompletedSkill(int studentId, String skillTag) {
        return completedSkills.contains(studentId + ":" + skillTag);
    }
}
