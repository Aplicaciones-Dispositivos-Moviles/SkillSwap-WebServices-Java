package com.innovify.skillswap.credentialverification.application.internal.outboundservices;

import java.time.Duration;

/**
 * Contract for storing the certificate files outside the database, decoupling the application from the
 * concrete provider. Files are private: they can only be read through a temporary URL.
 *
 * <p>Implementations signal any failure with an unchecked exception.
 */
public interface FileStorageService {

    /**
     * Stores a file and returns the reference needed to retrieve or delete it later.
     *
     * @param content     the raw content of the file
     * @param contentType the validated MIME type of the file
     * @param fileKey     the logical key (path) under which the file is stored
     */
    String upload(byte[] content, String contentType, String fileKey);

    /** Builds a signed URL that gives temporary read access to a stored file. */
    String getTemporaryUrl(String storageReference, Duration validFor);

    /** Deletes a stored file. */
    void delete(String storageReference);
}
