package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.learningpathengine.TestData;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeLearningPathRepository;
import com.innovify.skillswap.learningpathengine.application.fakes.FakeMiniProjectRepository;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetMiniProjectByPathNodeIdQuery;
import org.junit.jupiter.api.Test;

class MiniProjectQueryServiceImplTest {

    private final FakeMiniProjectRepository miniProjects = new FakeMiniProjectRepository();
    private final FakeLearningPathRepository paths = new FakeLearningPathRepository();
    private final MiniProjectQueryServiceImpl service = new MiniProjectQueryServiceImpl(paths, miniProjects);

    @Test
    void handle_returnsTheLatestMiniProjectOfTheNodeOfTheStudent() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
        miniProjects.save(new MiniProject(1, "networking-basics", TestData.miniProjectDraft()));
        MiniProject latest = miniProjects.save(new MiniProject(1, "networking-basics", TestData.miniProjectDraft()));

        assertThat(service.handle(new GetMiniProjectByPathNodeIdQuery(1, 1))).containsSame(latest);
    }

    @Test
    void handle_forANodeOfAnotherStudent_returnsEmpty() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));
        miniProjects.save(new MiniProject(1, "networking-basics", TestData.miniProjectDraft()));

        assertThat(service.handle(new GetMiniProjectByPathNodeIdQuery(1, 2))).isEmpty();
    }

    @Test
    void handle_forANodeWithoutMiniProject_orUnknown_returnsEmpty() {
        paths.save(TestData.newUnsavedPath(1, "authentication-jwt"));

        assertThat(service.handle(new GetMiniProjectByPathNodeIdQuery(2, 1))).isEmpty();
        assertThat(service.handle(new GetMiniProjectByPathNodeIdQuery(99, 1))).isEmpty();
    }
}
