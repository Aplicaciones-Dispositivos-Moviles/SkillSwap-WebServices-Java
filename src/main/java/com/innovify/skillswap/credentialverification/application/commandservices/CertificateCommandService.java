package com.innovify.skillswap.credentialverification.application.commandservices;

import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.commands.ResolveCertificateDisputeCommand;
import com.innovify.skillswap.credentialverification.domain.model.commands.UploadCertificateCommand;
import com.innovify.skillswap.shared.application.Result;

/** Certificate command service interface. */
public interface CertificateCommandService {

    /**
     * Validates the file, stores it, evaluates the risk and registers the certificate. When the same owner
     * already uploaded the file, fails with {@code DUPLICATE_FILE} and the id of the existing certificate in
     * the details ({@code existingCertificateId}).
     */
    Result<Certificate> handle(UploadCertificateCommand command);

    /** Applies the Coordinator's decision on a suspicious certificate. */
    Result<Certificate> handle(ResolveCertificateDisputeCommand command);
}
