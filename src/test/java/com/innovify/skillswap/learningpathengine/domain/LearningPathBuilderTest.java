package com.innovify.skillswap.learningpathengine.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.innovify.skillswap.learningpathengine.FakeSkillTaxonomy;
import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.NodeStatus;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultLearningPathBuilder;
import com.innovify.skillswap.learningpathengine.domain.services.DefaultSkillGapAnalyzer;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningPathBuilderTest {

    private final DefaultLearningPathBuilder builder = new DefaultLearningPathBuilder(TestData.TAXONOMY);

    private static SkillGap fullGap() {
        return new DefaultSkillGapAnalyzer(TestData.TAXONOMY).analyze(TestData.goal("authentication-jwt"), List.of());
    }

    @Test
    void buildPath_ordersTheSkillsByPrerequisites() {
        List<PathNode> nodes = builder.buildPath(fullGap());

        assertThat(nodes).extracting(PathNode::getSkillTag).containsExactly("networking-basics",
                "programming-fundamentals", "http-basics", "rest-api-design", "authentication-jwt");
        assertThat(nodes).extracting(PathNode::getOrder).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void buildPath_startsAvailableOnlyTheNodesWithoutPendingPrerequisites() {
        List<PathNode> nodes = builder.buildPath(fullGap());

        assertThat(nodes).extracting(PathNode::getStatus).containsExactly(NodeStatus.AVAILABLE,
                NodeStatus.AVAILABLE, NodeStatus.LOCKED, NodeStatus.LOCKED, NodeStatus.LOCKED);
    }

    @Test
    void buildPath_keepsOnlyThePrerequisitesThatAreInThePath() {
        List<PathNode> nodes = builder.buildPath(fullGap());

        PathNode rest = nodes.stream().filter(n -> n.getSkillTag().equals("rest-api-design")).findFirst().orElseThrow();
        assertThat(rest.getPrerequisiteSkillTags()).containsExactly("http-basics", "programming-fundamentals");
    }

    @Test
    void buildPath_whenPrerequisitesWereAlreadyDemonstrated_startsTheNodeAvailable() {
        SkillGap gap = new DefaultSkillGapAnalyzer(TestData.TAXONOMY)
                .analyze(TestData.goal("authentication-jwt"), List.of("rest-api-design"));

        List<PathNode> nodes = builder.buildPath(gap);

        assertThat(nodes).singleElement().satisfies(node -> {
            assertThat(node.getSkillTag()).isEqualTo("authentication-jwt");
            assertThat(node.getStatus()).isEqualTo(NodeStatus.AVAILABLE);
            assertThat(node.getPrerequisiteSkillTags()).isEmpty();
        });
    }

    @Test
    void buildPath_withAnEmptyGap_returnsNoNodes() {
        assertThat(builder.buildPath(new SkillGap(List.of(), List.of()))).isEmpty();
    }

    @Test
    void buildPath_isDeterministic() {
        List<String> first = builder.buildPath(fullGap()).stream().map(PathNode::getSkillTag).toList();
        List<String> second = builder.buildPath(fullGap()).stream().map(PathNode::getSkillTag).toList();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void buildPath_withACycleInTheTaxonomy_throws() {
        var cyclic = new FakeSkillTaxonomy(Map.of("a", List.of("b"), "b", List.of("a")));

        assertThatThrownBy(() -> new DefaultLearningPathBuilder(cyclic)
                .buildPath(new SkillGap(List.of(), List.of("a", "b")))).isInstanceOf(DomainException.class);
    }
}
