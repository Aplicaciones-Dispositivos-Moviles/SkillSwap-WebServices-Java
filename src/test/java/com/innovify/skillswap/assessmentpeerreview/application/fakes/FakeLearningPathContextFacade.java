package com.innovify.skillswap.assessmentpeerreview.application.fakes;

import com.innovify.skillswap.learningpathengine.application.acl.BlueprintQuestionView;
import com.innovify.skillswap.learningpathengine.application.acl.BlueprintView;
import com.innovify.skillswap.learningpathengine.application.acl.LearningPathContextFacade;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectRubricView;
import com.innovify.skillswap.learningpathengine.application.acl.MiniProjectView;
import com.innovify.skillswap.learningpathengine.application.acl.NodeCompletionOutcome;
import com.innovify.skillswap.learningpathengine.application.acl.NodeReopenOutcome;
import com.innovify.skillswap.learningpathengine.application.acl.PathCompletionView;
import com.innovify.skillswap.learningpathengine.application.acl.RubricCriterionView;
import java.time.Instant;
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

    /** The rubric of every mini-project added (weights 40, 30, 20 and 10); its passing score is 70. */
    public static final List<RubricCriterionView> RUBRIC = List.of(
            new RubricCriterionView("Functionality", "It does what the statement asks.", 40),
            new RubricCriterionView("Code quality", "The code is organized and tested.", 30),
            new RubricCriterionView("Documentation", "The README explains how to run it.", 20),
            new RubricCriterionView("Version control", "The history is meaningful.", 10));

    private final Map<Integer, BlueprintView> blueprints = new HashMap<>();
    private final Map<Integer, MiniProjectView> miniProjects = new HashMap<>();
    private final Map<Integer, PathCompletionView> paths = new HashMap<>();
    private final Set<String> completedSkills = new HashSet<>();
    private final List<Integer> completedNodes = new ArrayList<>();
    private final List<Integer> reopenedNodes = new ArrayList<>();
    private NodeReopenOutcome nextReopenOutcome = NodeReopenOutcome.REOPENED;
    private NodeCompletionOutcome nextOutcome = NodeCompletionOutcome.COMPLETED;

    public List<Integer> completedNodes() {
        return completedNodes;
    }

    public List<Integer> reopenedNodes() {
        return reopenedNodes;
    }

    /** What {@link #reopenNode} reports; the node is recorded only when it is REOPENED. */
    public void setNextReopenOutcome(NodeReopenOutcome nextReopenOutcome) {
        this.nextReopenOutcome = nextReopenOutcome;
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

    /** The blueprint 1 of {@link #addBlueprint()}, with a sub-topic for each of its questions. */
    public BlueprintView addBlueprintWithSubTopics(List<String> subTopics) {
        return addBlueprint(1, 10, 1, "http-basics", true, true, subTopics);
    }

    public BlueprintView addBlueprint(int blueprintId, int pathNodeId, int studentId, String skillTag,
                                      boolean isLatest, boolean nodeIsAvailable) {
        return addBlueprint(blueprintId, pathNodeId, studentId, skillTag, isLatest, nodeIsAvailable, null);
    }

    private BlueprintView addBlueprint(int blueprintId, int pathNodeId, int studentId, String skillTag,
                                       boolean isLatest, boolean nodeIsAvailable, List<String> subTopics) {
        List<BlueprintQuestionView> questions = new ArrayList<>();
        for (int index = 0; index < CORRECT.size(); index++) {
            int number = index + 1;
            questions.add(new BlueprintQuestionView("Question " + number + "?",
                    List.of("a" + number, "b" + number, "c" + number, "d" + number), CORRECT.get(index),
                    subTopics == null ? null : subTopics.get(index)));
        }
        BlueprintView view = new BlueprintView(blueprintId, pathNodeId, studentId, skillTag, isLatest,
                nodeIsAvailable, questions);
        blueprints.put(blueprintId, view);
        return view;
    }

    /** The mini-project 1 of node 10, owned by student 1, about "http-basics", due in five days. */
    public MiniProjectView addMiniProject() {
        return addMiniProject(1, 10, 1, "http-basics", java.time.Instant.now().plus(java.time.Duration.ofDays(5)),
                true, true);
    }

    public MiniProjectView addMiniProject(int miniProjectId, int pathNodeId, int studentId, String skillTag,
                                          java.time.Instant dueAt, boolean isLatest, boolean nodeIsAvailable) {
        MiniProjectView view = new MiniProjectView(miniProjectId, pathNodeId, studentId, skillTag, dueAt, isLatest,
                nodeIsAvailable);
        miniProjects.put(miniProjectId, view);
        return view;
    }

    @Override
    public Optional<MiniProjectView> getMiniProject(int miniProjectId) {
        return Optional.ofNullable(miniProjects.get(miniProjectId));
    }

    @Override
    public Optional<MiniProjectRubricView> getMiniProjectRubric(int miniProjectId) {
        MiniProjectView view = miniProjects.get(miniProjectId);
        if (view == null) {
            return Optional.empty();
        }
        return Optional.of(new MiniProjectRubricView(miniProjectId, view.skillTag(), "Mini-project " + miniProjectId,
                "Build a small project that demonstrates " + view.skillTag() + ".", RUBRIC, 70));
    }

    @Override
    public NodeReopenOutcome reopenNode(int pathNodeId) {
        if (nextReopenOutcome == NodeReopenOutcome.REOPENED) {
            reopenedNodes.add(pathNodeId);
        }
        return nextReopenOutcome;
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

    /** Adds a path of the student with the given skills. */
    public PathCompletionView addPath(int pathId, int studentId, boolean completed, String... skillTags) {
        PathCompletionView view = new PathCompletionView(pathId, studentId, completed, List.of(skillTags));
        paths.put(pathId, view);
        return view;
    }

    @Override
    public Optional<PathCompletionView> getPathCompletion(int pathId) {
        return Optional.ofNullable(paths.get(pathId));
    }

    @Override
    public boolean hasCompletedSkill(int studentId, String skillTag) {
        return completedSkills.contains(studentId + ":" + skillTag);
    }

    @Override
    public List<com.innovify.skillswap.learningpathengine.application.acl.SkillDemandView> getMostRequestedSkills(
            Instant from, Instant to, int limit) {
        return List.of();
    }
}
