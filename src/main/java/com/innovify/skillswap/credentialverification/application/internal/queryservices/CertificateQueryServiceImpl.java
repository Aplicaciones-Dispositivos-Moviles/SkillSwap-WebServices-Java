package com.innovify.skillswap.credentialverification.application.internal.queryservices;

import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import com.innovify.skillswap.credentialverification.application.queryservices.CertificateQueryService;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificateByIdQuery;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificatesByOwnerIdQuery;
import com.innovify.skillswap.credentialverification.domain.repositories.CertificateRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service
public class CertificateQueryServiceImpl implements CertificateQueryService {

    public static final Duration FILE_URL_LIFETIME = Duration.ofMinutes(15);

    private final CertificateRepository certificateRepository;
    private final FileStorageService fileStorageService;

    public CertificateQueryServiceImpl(CertificateRepository certificateRepository,
                                       FileStorageService fileStorageService) {
        this.certificateRepository = certificateRepository;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public Optional<Certificate> handle(GetCertificateByIdQuery query) {
        return certificateRepository.findById(query.certificateId());
    }

    @Override
    public List<Certificate> handle(GetCertificatesByOwnerIdQuery query) {
        return certificateRepository.findByOwnerId(query.ownerId());
    }

    @Override
    public String getFileUrl(Certificate certificate) {
        return fileStorageService.getTemporaryUrl(certificate.getStorageReference(), FILE_URL_LIFETIME);
    }
}
