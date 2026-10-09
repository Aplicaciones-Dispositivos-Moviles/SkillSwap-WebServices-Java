package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.repositories.VerifierProfileRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link VerifierProfileRepository} port on top of Spring Data JPA. */
@Repository
public class VerifierProfileRepositoryAdapter implements VerifierProfileRepository {

    private final VerifierProfileJpaRepository jpaRepository;

    public VerifierProfileRepositoryAdapter(VerifierProfileJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public VerifierProfile save(VerifierProfile profile) {
        return jpaRepository.saveAndFlush(profile);
    }

    @Override
    public Optional<VerifierProfile> findByUserId(int userId) {
        return jpaRepository.findByVerifierUserId(userId);
    }

    @Override
    public List<VerifierProfile> findEnabledBySkillTag(String skillTag) {
        // The skills live in a jsonb column that cannot be searched with a derived query, so the skill is
        // filtered in memory over the (few) profiles that were not revoked.
        return jpaRepository.findByVerifiedTrue().stream()
                .filter(profile -> profile.canReview(skillTag))
                .toList();
    }

    @Override
    public List<VerifierProfile> findEnabled() {
        return jpaRepository.findByVerifiedTrue();
    }
}
