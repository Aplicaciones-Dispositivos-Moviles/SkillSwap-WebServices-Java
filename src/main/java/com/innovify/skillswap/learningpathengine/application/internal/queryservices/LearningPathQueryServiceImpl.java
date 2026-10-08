package com.innovify.skillswap.learningpathengine.application.internal.queryservices;

import com.innovify.skillswap.learningpathengine.application.queryservices.LearningPathQueryService;
import com.innovify.skillswap.learningpathengine.domain.model.aggregates.LearningPath;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetLearningPathByStudentIdQuery;
import com.innovify.skillswap.learningpathengine.domain.repositories.LearningPathRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LearningPathQueryServiceImpl implements LearningPathQueryService {

    private final LearningPathRepository learningPathRepository;

    public LearningPathQueryServiceImpl(LearningPathRepository learningPathRepository) {
        this.learningPathRepository = learningPathRepository;
    }

    @Override
    public Optional<LearningPath> handle(GetLearningPathByStudentIdQuery query) {
        return learningPathRepository.findLatestByStudentId(query.studentId());
    }
}
