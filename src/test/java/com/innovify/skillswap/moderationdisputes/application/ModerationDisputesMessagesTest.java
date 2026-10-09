package com.innovify.skillswap.moderationdisputes.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.innovify.skillswap.assessmentpeerreview.application.fakes.TestMessages;
import com.innovify.skillswap.moderationdisputes.domain.model.ModerationDisputesError;
import com.innovify.skillswap.moderationdisputes.interfaces.rest.transform.ModerationDisputesActionResultAssembler;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;

class ModerationDisputesMessagesTest {

    @ParameterizedTest
    @ValueSource(strings = {"en-US", "es-419"})
    void everyError_hasAMessageInEveryLanguage(String languageTag) {
        var source = TestMessages.source();
        Locale locale = Locale.forLanguageTag(languageTag);

        for (ModerationDisputesError error : ModerationDisputesError.values()) {
            if (error == ModerationDisputesError.NONE) {
                continue;
            }
            String code = ErrorCodes.of(error);
            try {
                assertThat(source.getMessage(code, null, locale)).as(code).isNotBlank();
            } catch (NoSuchMessageException e) {
                throw new AssertionError("Missing message '" + code + "' for " + languageTag, e);
            }
        }
    }

    @Test
    void codes_andStatuses_areTheOnesOfTheApi() {
        assertThat(Arrays.stream(ModerationDisputesError.values()).map(ErrorCodes::of)).containsExactly(
                "None", "InvalidOutcome", "InvalidDisputeStatus", "CoordinatorNotesRequired",
                "CoordinatorNotesTooLong", "DisputeNotFound", "NotAVerifier", "NotAssignedReviewer",
                "DisputeAlreadyResolved", "CertificateNotSuspicious", "OperationCancelled", "DatabaseError",
                "InternalServerError");
        assertThat(ModerationDisputesActionResultAssembler.toStatusFromError(ModerationDisputesError.INVALID_OUTCOME))
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ModerationDisputesActionResultAssembler.toStatusFromError(
                ModerationDisputesError.NOT_ASSIGNED_REVIEWER)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ModerationDisputesActionResultAssembler.toStatusFromError(
                ModerationDisputesError.DISPUTE_ALREADY_RESOLVED)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ModerationDisputesActionResultAssembler.toStatusFromError(
                ModerationDisputesError.DISPUTE_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
