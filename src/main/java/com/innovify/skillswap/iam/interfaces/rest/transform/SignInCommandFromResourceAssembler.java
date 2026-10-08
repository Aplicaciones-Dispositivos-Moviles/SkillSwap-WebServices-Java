package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.interfaces.rest.resources.SignInResource;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;

public final class SignInCommandFromResourceAssembler {

    private SignInCommandFromResourceAssembler() {
    }

    public static SignInCommand toCommandFromResource(SignInResource resource) {
        return new SignInCommand(resource.username().trim(), resource.password());
    }
}
