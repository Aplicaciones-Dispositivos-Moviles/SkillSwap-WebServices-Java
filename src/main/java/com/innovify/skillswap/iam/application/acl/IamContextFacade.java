package com.innovify.skillswap.iam.application.acl;

import java.util.Optional;

/**
 * Anti-corruption facade through which other bounded contexts read account data, without depending on the
 * {@code User} aggregate or its repository.
 */
public interface IamContextFacade {

    /** The real name the user registered; empty when the user does not exist or gave no name. */
    Optional<String> getRegisteredFullName(int userId);
}
