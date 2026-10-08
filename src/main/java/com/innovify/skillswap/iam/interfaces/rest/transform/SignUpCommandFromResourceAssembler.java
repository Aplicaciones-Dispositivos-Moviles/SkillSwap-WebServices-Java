package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.interfaces.rest.resources.SignUpResource;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;

public final class SignUpCommandFromResourceAssembler {

    private SignUpCommandFromResourceAssembler() {
    }

    /** The password is passed through untouched: trimming it would change what the user typed. */
    public static SignUpCommand toCommandFromResource(SignUpResource resource, UserRole role) {
        return new SignUpCommand(resource.username().trim(), resource.email().trim(), resource.password(), role);
    }
}
