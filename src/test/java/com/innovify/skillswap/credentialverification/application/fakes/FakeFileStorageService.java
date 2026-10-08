package com.innovify.skillswap.credentialverification.application.fakes;

import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Records what would be stored and deleted, without touching any real storage. */
public class FakeFileStorageService implements FileStorageService {

    /** One recorded upload. */
    public record Upload(String key, String contentType, int length) {
    }

    private final List<Upload> uploads = new ArrayList<>();
    private final List<String> deleted = new ArrayList<>();
    private RuntimeException uploadFailure;

    public List<Upload> uploads() {
        return uploads;
    }

    public List<String> deleted() {
        return deleted;
    }

    /** Makes every following upload throw the given exception. */
    public void failOnUpload(RuntimeException failure) {
        this.uploadFailure = failure;
    }

    @Override
    public String upload(byte[] content, String contentType, String fileKey) {
        if (uploadFailure != null) {
            throw uploadFailure;
        }
        uploads.add(new Upload(fileKey, contentType, content.length));
        return "stored/" + fileKey;
    }

    @Override
    public String getTemporaryUrl(String storageReference, Duration validFor) {
        return "https://files.test/" + storageReference + "?minutes=" + validFor.toMinutes();
    }

    @Override
    public void delete(String storageReference) {
        deleted.add(storageReference);
    }
}
