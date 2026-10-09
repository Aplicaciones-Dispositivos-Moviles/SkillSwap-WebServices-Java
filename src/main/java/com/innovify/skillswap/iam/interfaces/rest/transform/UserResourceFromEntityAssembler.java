package com.innovify.skillswap.iam.interfaces.rest.transform;

import com.innovify.skillswap.iam.interfaces.rest.resources.UserResource;
import com.innovify.skillswap.iam.domain.model.aggregates.User;

public final class UserResourceFromEntityAssembler {

    private UserResourceFromEntityAssembler() {
    }

    public static UserResource toResourceFromEntity(User entity) {
        return new UserResource(entity.getId(), entity.getUsername().value(), entity.getEmail().value(),
                entity.getRole().value(), entity.isVerified(), entity.getBio(), entity.getInterestTopics(),
                entity.getSkillVector());
    }
}
