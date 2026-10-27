package com.innovify.skillswap.credentialverification.application.acl;

import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class FileStorageContextFacadeImpl implements FileStorageContextFacade {

    private final FileStorageService fileStorageService;

    public FileStorageContextFacadeImpl(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @Override
    public String upload(byte[] content, String contentType, String fileKey) {
        return fileStorageService.upload(content, contentType, fileKey);
    }

    @Override
    public String getTemporaryUrl(String storageReference, Duration validFor) {
        return fileStorageService.getTemporaryUrl(storageReference, validFor);
    }

    @Override
    public void delete(String storageReference) {
        fileStorageService.delete(storageReference);
    }
}
