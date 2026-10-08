package com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to the "certificates" table. Only {@link CertificateRepositoryAdapter} uses it. */
public interface CertificateJpaRepository extends JpaRepository<Certificate, Integer> {

    List<Certificate> findByOwnerIdOrderByCreatedAtDescIdDesc(int ownerId);

    Optional<Certificate> findByOwnerIdAndFileHash(int ownerId, String fileHash);

    boolean existsByOwnerIdNotAndCertificateNumber(int ownerId, String certificateNumber);

    boolean existsByOwnerIdNotAndVerificationCode(int ownerId, String verificationCode);

    boolean existsByOwnerIdNotAndFileHash(int ownerId, String fileHash);
}
