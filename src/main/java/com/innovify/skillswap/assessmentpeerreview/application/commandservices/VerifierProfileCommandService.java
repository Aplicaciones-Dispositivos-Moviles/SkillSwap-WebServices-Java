package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.VerifierProfile;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.CreateVerifierProfileCommand;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.UpdateVerifierAvailabilityCommand;
import com.innovify.skillswap.shared.application.Result;

/** Verifier profile command service interface. */
public interface VerifierProfileCommandService {

    /** Enables the student as a verifier of a skill they completed, creating the profile if needed. */
    Result<VerifierProfile> handle(CreateVerifierProfileCommand command);

    /** Switches the availability of the verifier; turning it on puts them back in the queue. */
    Result<VerifierProfile> handle(UpdateVerifierAvailabilityCommand command);
}
