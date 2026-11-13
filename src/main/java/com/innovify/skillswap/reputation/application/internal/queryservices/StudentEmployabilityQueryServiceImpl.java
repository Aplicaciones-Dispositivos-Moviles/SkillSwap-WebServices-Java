package com.innovify.skillswap.reputation.application.internal.queryservices;

import com.innovify.skillswap.reputation.application.queryservices.StudentEmployabilityQueryService;
import com.innovify.skillswap.reputation.domain.model.aggregates.StudentEmployabilityScore;
import com.innovify.skillswap.reputation.domain.model.queries.GetStudentEmployabilityByStudentIdQuery;
import com.innovify.skillswap.reputation.domain.repositories.StudentEmployabilityScoreRepository;
import java.util.Optional;

public class StudentEmployabilityQueryServiceImpl implements StudentEmployabilityQueryService {

    private final StudentEmployabilityScoreRepository repository;

    public StudentEmployabilityQueryServiceImpl(StudentEmployabilityScoreRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<StudentEmployabilityScore> handle(GetStudentEmployabilityByStudentIdQuery query) {
        return repository.findByStudentId(query.studentId());
    }
}
