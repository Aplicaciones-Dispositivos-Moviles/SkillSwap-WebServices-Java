package com.innovify.skillswap.shared.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultTest {

    private enum SampleError { CONFLICT }

    @Test
    void success_hasNoErrorAndNoDetails() {
        Result<String> result = Result.success("ok");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isEqualTo("ok");
        assertThat(result.error()).isNull();
        assertThat(result.details()).isNull();
    }

    @Test
    void failure_withoutDetails_hasNoDetails() {
        Result<String> result = Result.failure(SampleError.CONFLICT, "message");

        assertThat(result.isFailure()).isTrue();
        assertThat(result.error()).isEqualTo(SampleError.CONFLICT);
        assertThat(result.message()).isEqualTo("message");
        assertThat(result.details()).isNull();
    }

    @Test
    void failure_withDetails_exposesThem() {
        Result<String> result = Result.failure(SampleError.CONFLICT, "message", Map.of("existingId", 12));

        assertThat(result.details()).containsEntry("existingId", 12);
    }

    @Test
    void voidSuccess_hasNoValue() {
        Result<Void> result = Result.success();

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.value()).isNull();
    }
}
