package com.innovify.skillswap.credentialverification.domain.repositories;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import java.util.List;
import java.util.Optional;

/** Persistence port of the {@link Certificate} aggregate, including the queries that detect duplicates. */
public interface CertificateRepository {

    /**
     * Persists a new or updated certificate and flushes right away, so integrity errors (e.g. the same file
     * registered twice by one owner) surface at the call.
     */
    Certificate save(Certificate certificate);

    Optional<Certificate> findById(int id);

    /** The certificates of an owner, newest first. */
    List<Certificate> findByOwnerId(int ownerId);

    /** The certificate this owner already registered with this file hash, if any. */
    Optional<Certificate> findByFileHash(int ownerId, String fileHash);

    /** Whether a different user already registered this certificate number. */
    boolean existsByCertificateNumberExcludingOwner(int ownerId, String certificateNumber);

    /** Whether a different user already registered this verification code. */
    boolean existsByVerificationCodeExcludingOwner(int ownerId, String verificationCode);

    /** Whether a different user already uploaded a file with this hash. */
    boolean existsByFileHashExcludingOwner(int ownerId, String fileHash);
}
