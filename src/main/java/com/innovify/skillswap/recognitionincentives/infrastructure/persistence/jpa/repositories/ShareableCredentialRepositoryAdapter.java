package com.innovify.skillswap.recognitionincentives.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import com.innovify.skillswap.recognitionincentives.domain.repositories.ShareableCredentialRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link ShareableCredentialRepository} port on top of Spring Data JPA. */
@Repository
public class ShareableCredentialRepositoryAdapter implements ShareableCredentialRepository {

    private final ShareableCredentialJpaRepository jpaRepository;

    public ShareableCredentialRepositoryAdapter(ShareableCredentialJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public ShareableCredential save(ShareableCredential credential) {
        return jpaRepository.saveAndFlush(credential);
    }

    @Override
    public Optional<ShareableCredential> findByToken(String token) {
        return jpaRepository.findByToken(token);
    }

    @Override
    public Optional<ShareableCredential> findByOwnerIdAndSkillTag(int ownerId, String skillTag) {
        return jpaRepository.findByOwnerIdAndSkillTag(ownerId, skillTag);
    }

    @Override
    public List<ShareableCredential> findByOwnerId(int ownerId) {
        return jpaRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId);
    }
}
