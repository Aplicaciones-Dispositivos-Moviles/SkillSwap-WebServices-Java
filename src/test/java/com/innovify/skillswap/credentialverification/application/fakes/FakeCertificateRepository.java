package com.innovify.skillswap.credentialverification.application.fakes;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

/** In-memory repository that assigns ids like the database does. */
public class FakeCertificateRepository implements CertificateRepository {

    private final List<Certificate> certificates = new ArrayList<>();
    private int nextId = 1;
    private int saveCalls;
    private RuntimeException saveFailure;

    public List<Certificate> certificates() {
        return certificates;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public Certificate save(Certificate certificate) {
        saveCalls++;
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (certificate.getId() == null) {
            ReflectionTestUtils.setField(certificate, "id", nextId++);
        }
        if (!certificates.contains(certificate)) {
            certificates.add(certificate);
        }
        return certificate;
    }

    @Override
    public Optional<Certificate> findById(int id) {
        return certificates.stream().filter(c -> Objects.equals(c.getId(), id)).findFirst();
    }

    @Override
    public List<Certificate> findByOwnerId(int ownerId) {
        return certificates.stream()
                .filter(c -> c.getOwnerId() == ownerId)
                .sorted(Comparator.comparing(Certificate::getCreatedAt).reversed()
                        .thenComparing(Comparator.comparing(Certificate::getId).reversed()))
                .toList();
    }

    @Override
    public Optional<Certificate> findByFileHash(int ownerId, String fileHash) {
        return certificates.stream()
                .filter(c -> c.getOwnerId() == ownerId && c.getFileHash().equals(fileHash))
                .findFirst();
    }

    @Override
    public boolean existsByCertificateNumberExcludingOwner(int ownerId, String certificateNumber) {
        return certificates.stream()
                .anyMatch(c -> c.getOwnerId() != ownerId && certificateNumber.equals(c.getCertificateNumber()));
    }

    @Override
    public boolean existsByVerificationCodeExcludingOwner(int ownerId, String verificationCode) {
        return certificates.stream()
                .anyMatch(c -> c.getOwnerId() != ownerId && verificationCode.equals(c.getVerificationCode()));
    }

    @Override
    public boolean existsByFileHashExcludingOwner(int ownerId, String fileHash) {
        return certificates.stream()
                .anyMatch(c -> c.getOwnerId() != ownerId && c.getFileHash().equals(fileHash));
    }
}
