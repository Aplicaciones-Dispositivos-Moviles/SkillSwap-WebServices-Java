package com.innovify.skillswap.credentialverification.application.queryservices;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificateByIdQuery;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificatesByOwnerIdQuery;
import java.util.List;
import java.util.Optional;

/** Certificate query service interface. */
public interface CertificateQueryService {

    Optional<Certificate> handle(GetCertificateByIdQuery query);

    /** The certificates of an owner, newest first. */
    List<Certificate> handle(GetCertificatesByOwnerIdQuery query);

    /** A temporary signed URL to view the certificate file. */
    String getFileUrl(Certificate certificate);
}
