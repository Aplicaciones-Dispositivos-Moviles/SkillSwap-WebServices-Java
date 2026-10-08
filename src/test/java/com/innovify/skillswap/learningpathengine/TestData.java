package com.innovify.skillswap.learningpathengine;

import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.entities.Question;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.test.util.ReflectionTestUtils;

/** Shared builders for the Learning Path Engine tests. */
public final class TestData {

    public static final FakeSkillTaxonomy TAXONOMY = FakeSkillTaxonomy.sample();

    private TestData() {
    }

    public static CareerGoal goal(String... tags) {
        return new CareerGoal("I want to build APIs", List.of(tags));
    }

    /**
     * Builds a path through the real analyzer and builder and assigns node ids 1..n in path order, as the
     * database would. For the goal "authentication-jwt" the nodes are: 1 networking-basics,
     * 2 programming-fundamentals (both available), 3 http-basics, 4 rest-api-design and
     * 5 authentication-jwt (locked).
     */
    public static LearningPath newPath() {
        return newPath(1, "authentication-jwt");
    }

    public static LearningPath newPath(int studentId, String... goalTags) {
        LearningPath path = newUnsavedPath(studentId, goalTags);
        assignNodeIds(path);
        return path;
    }

    /** Same path as {@link #newPath(int, String...)}, but the nodes keep no id: the database assigns them. */
    public static LearningPath newUnsavedPath(int studentId, String... goalTags) {
        CareerGoal goal = goal(goalTags.length == 0 ? new String[]{"authentication-jwt"} : goalTags);
        var gap = new DefaultSkillGapAnalyzer(TAXONOMY).analyze(goal, List.of());
        return new LearningPath(studentId, goal, new DefaultLearningPathBuilder(TAXONOMY).buildPath(gap));
    }

    public static void assignNodeIds(LearningPath path) {
        int id = 1;
        for (PathNode node : path.getNodes()) {
            ReflectionTestUtils.setField(node, "id", id++);
        }
    }

    public static Question question(int index) {
        return new Question("Question " + index + "?",
                List.of("a" + index, "b" + index, "c" + index, "d" + index), index % 4);
    }

    public static List<Question> questions(int count) {
        List<Question> questions = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            questions.add(question(index));
        }
        return questions;
    }
}
