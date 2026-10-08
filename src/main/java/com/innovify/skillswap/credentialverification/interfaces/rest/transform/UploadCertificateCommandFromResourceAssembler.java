package com.innovify.skillswap.credentialverification.interfaces.rest.transform;

import com.innovify.skillswap.credentialverification.domain.model.commands.UploadCertificateCommand;
import com.innovify.skillswap.credentialverification.interfaces.rest.resources.UploadCertificateResource;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.web.multipart.MultipartFile;

public final class UploadCertificateCommandFromResourceAssembler {

    private UploadCertificateCommandFromResourceAssembler() {
    }

    /**
     * A missing or empty file becomes an empty content, which the application layer rejects as FILE_REQUIRED.
     * The owner comes from the authenticated user, never from the form.
     */
    public static UploadCertificateCommand toCommandFromResource(UploadCertificateResource resource, int ownerId) {
        MultipartFile file = resource.file();
        byte[] content = new byte[0];
        if (file != null && !file.isEmpty()) {
            try {
                content = file.getBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("The uploaded file could not be read.", e);
            }
        }

        return new UploadCertificateCommand(
                ownerId,
                file == null || file.getContentType() == null ? "" : file.getContentType(),
                content,
                resource.holderName(),
                resource.institutionName(),
                resource.courseName(),
                resource.issueDate(),
                resource.durationHours(),
                resource.certificateNumber(),
                resource.verificationCode(),
                resource.verificationUrl(),
                resource.qrPayload(),
                resource.ocrText());
    }
}
