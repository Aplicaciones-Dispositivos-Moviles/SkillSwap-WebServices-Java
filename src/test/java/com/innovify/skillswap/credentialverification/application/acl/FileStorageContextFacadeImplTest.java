package com.innovify.skillswap.credentialverification.application.acl;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.credentialverification.application.fakes.FakeFileStorageService;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class FileStorageContextFacadeImplTest {

    private final FakeFileStorageService storage = new FakeFileStorageService();
    private final FileStorageContextFacade facade = new FileStorageContextFacadeImpl(storage);

    @Test
    void upload_storesTheFileAndReturnsItsReference() {
        String reference = facade.upload(new byte[]{1, 2, 3}, "video/mp4", "demonstrations/4/abc");

        assertThat(reference).isEqualTo("stored/demonstrations/4/abc");
        assertThat(storage.uploads()).hasSize(1);
        assertThat(storage.uploads().get(0).contentType()).isEqualTo("video/mp4");
        assertThat(storage.uploads().get(0).length()).isEqualTo(3);
    }

    @Test
    void getTemporaryUrl_andDelete_goToTheStorage() {
        assertThat(facade.getTemporaryUrl("stored/x.mp4", Duration.ofMinutes(15)))
                .isEqualTo("https://files.test/stored/x.mp4?minutes=15");

        facade.delete("stored/x.mp4");

        assertThat(storage.deleted()).containsExactly("stored/x.mp4");
    }
}
