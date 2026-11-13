package com.innovify.skillswap.support;

import com.innovify.skillswap.credentialverification.application.fakes.FakeFileStorageService;
import com.innovify.skillswap.credentialverification.application.internal.outboundservices.FileStorageService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces the Cloudinary storage with an in-memory fake, so the integration tests never use the network. */
@TestConfiguration
public class FileStorageTestConfig {

    @Bean
    @Primary
    public FileStorageService testFileStorageService() {
        return new FakeFileStorageService();
    }
}
