package com.innovify.skillswap.credentialverification.application.acl;

import java.time.Duration;

/**
 * Anti-corruption facade through which other bounded contexts store private files (the video of a final
 * demonstration, for example) in the same storage as the certificates, without depending on its provider. Files are
 * private: they can only be read through a temporary URL. A failure is signaled with an unchecked exception.
 */
public interface FileStorageContextFacade {

    /**
     * Stores a file and returns the reference needed to read or delete it later.
     *
     * @param contentType the validated MIME type (image/jpeg, image/png, application/pdf, video/mp4, video/webm or
     *                    video/quicktime)
     * @param fileKey     the logical key (path) under which the file is stored
     */
    String upload(byte[] content, String contentType, String fileKey);

    /** A signed URL that gives temporary read access to a stored file. */
    String getTemporaryUrl(String storageReference, Duration validFor);

    /** Deletes a stored file. */
    void delete(String storageReference);
}
