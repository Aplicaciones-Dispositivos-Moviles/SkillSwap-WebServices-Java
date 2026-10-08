package com.innovify.skillswap.credentialverification.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/** Implements the domain {@link CertificateRepository} port on top of Spring Data JPA. */
@Repository
public class CertificateRepositoryAdapter implements CertificateRepository {

    private final CertificateJpaRepository jpaRepository;

    public CertificateRepositoryAdapter(CertificateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Certificate save(Certificate certificate) {
        return jpaRepository.saveAndFlush(certificate);
    }

    @Override
    public Optional<Certificate> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Certificate> findByOwnerId(int ownerId) {
        return jpaRepository.findByOwnerIdOrderByCreatedAtDescIdDesc(ownerId);
    }

    @Override
    public Optional<Certificate> findByFileHash(int ownerId, String fileHash) {
        return jpaRepository.findByOwnerIdAndFileHash(ownerId, fileHash);
    }

    @Override
    public boolean existsByCertificateNumberExcludingOwner(int ownerId, String certificateNumber) {
        return jpaRepository.existsByOwnerIdNotAndCertificateNumber(ownerId, certificateNumber);
    }

    @Override
    public boolean existsByVerificationCodeExcludingOwner(int ownerId, String verificationCode) {
        return jpaRepository.existsByOwnerIdNotAndVerificationCode(ownerId, verificationCode);
    }

    @Override
    public boolean existsByFileHashExcludingOwner(int ownerId, String fileHash) {
        return jpaRepository.existsByOwnerIdNotAndFileHash(ownerId, fileHash);
    }
}
