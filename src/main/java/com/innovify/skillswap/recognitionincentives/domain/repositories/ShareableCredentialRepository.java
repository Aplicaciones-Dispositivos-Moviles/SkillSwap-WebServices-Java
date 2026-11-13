package com.innovify.skillswap.recognitionincentives.domain.repositories;

import com.innovify.skillswap.recognitionincentives.domain.model.aggregates.ShareableCredential;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link ShareableCredential} aggregate. */
public interface ShareableCredentialRepository {

    /** Persists a new credential and flushes right away. */
    ShareableCredential save(ShareableCredential credential);

    Optional<ShareableCredential> findByToken(String token);

    Optional<ShareableCredential> findByOwnerIdAndSkillTag(int ownerId, String skillTag);

    /** The credentials of the user, the newest first. */
    List<ShareableCredential> findByOwnerId(int ownerId);
}
