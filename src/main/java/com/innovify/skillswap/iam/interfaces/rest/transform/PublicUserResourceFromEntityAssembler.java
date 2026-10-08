package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.interfaces.rest.resources.PublicUserResource;
import com.innovify.skillswap.iam.domain.model.aggregates.User;

public final class PublicUserResourceFromEntityAssembler {

    private PublicUserResourceFromEntityAssembler() {
    }

    public static PublicUserResource toResourceFromEntity(User entity) {
        return new PublicUserResource(entity.getId(), entity.getUsername().value(), entity.getRole().value(),
                entity.isVerified(), entity.getBio());
    }
}
