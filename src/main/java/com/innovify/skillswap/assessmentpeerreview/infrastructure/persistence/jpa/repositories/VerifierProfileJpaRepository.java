package com.innovify.skillswap.assessmentpeerreview.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "verifier_profiles" table. Only {@link VerifierProfileRepositoryAdapter} uses it. */
public interface VerifierProfileJpaRepository extends JpaRepository<VerifierProfile, Integer> {

    Optional<VerifierProfile> findByVerifierUserId(int verifierUserId);

    List<VerifierProfile> findByVerifiedTrue();
}
