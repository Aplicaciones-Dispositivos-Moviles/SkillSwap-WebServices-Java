package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "shareable_credentials" table. Only {@link ShareableCredentialRepositoryAdapter} uses it. */
public interface ShareableCredentialJpaRepository extends JpaRepository<ShareableCredential, Integer> {

    Optional<ShareableCredential> findByToken(String token);

    Optional<ShareableCredential> findByOwnerIdAndSkillTag(int ownerId, String skillTag);

    List<ShareableCredential> findByOwnerIdOrderByCreatedAtDescIdDesc(int ownerId);
}
