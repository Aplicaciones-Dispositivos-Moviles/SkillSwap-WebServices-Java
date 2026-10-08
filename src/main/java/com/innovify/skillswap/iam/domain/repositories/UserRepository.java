package com.innovify.skillswap.iam.domain.repositories;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import java.util.Optional;

/** Persistence port of the {@link User} aggregate. */
public interface UserRepository {

    /**
     * Persists a new or updated user and flushes right away, so integrity errors (e.g. a duplicated
     * username) surface at the call instead of at the end of the transaction.
     */
    User save(User user);

    Optional<User> findById(int id);

    Optional<User> findByUsername(Username username);

    Optional<User> findByEmail(Email email);

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);
}
