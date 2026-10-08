package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.interfaces.rest.resources.AuthenticatedUserResource;
import com.innovify.skillswap.iam.domain.model.aggregates.User;

public final class AuthenticatedUserResourceFromEntityAssembler {

    private AuthenticatedUserResourceFromEntityAssembler() {
    }

    public static AuthenticatedUserResource toResourceFromEntity(User entity, String token) {
        return new AuthenticatedUserResource(entity.getId(), entity.getUsername().value(),
                entity.getEmail().value(), entity.getRole().value(), entity.isVerified(), token);
    }
}
