package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import com.innovify.skillswap.learningpathengine.application.queryservices.MiniProjectQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.MiniProject;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetMiniProjectByPathNodeIdQuery;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import com.innovify.skillswap.learningpathengine.domain.repositories.MiniProjectRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class MiniProjectQueryServiceImpl implements MiniProjectQueryService {

    private final LearningPathRepository learningPathRepository;
    private final MiniProjectRepository miniProjectRepository;

    public MiniProjectQueryServiceImpl(LearningPathRepository learningPathRepository,
                                       MiniProjectRepository miniProjectRepository) {
        this.learningPathRepository = learningPathRepository;
        this.miniProjectRepository = miniProjectRepository;
    }

    @Override
    public Optional<MiniProject> handle(GetMiniProjectByPathNodeIdQuery query) {
        boolean ownsTheNode = learningPathRepository.findByNodeId(query.pathNodeId())
                .map(path -> path.getStudentId() == query.studentId())
                .orElse(false);
        if (!ownsTheNode) {
            return Optional.empty();
        }
        return miniProjectRepository.findLatestByPathNodeId(query.pathNodeId());
    }
}
