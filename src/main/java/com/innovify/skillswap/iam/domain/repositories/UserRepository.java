package com.innovify.skillswap.iam.domain.repositories;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.DeviceToken;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import java.util.List;
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

    /** The account whose pending email verification token has this SHA-256 hash. */
    Optional<User> findByVerificationTokenHash(String tokenHash);

    /** The accounts that registered this device token (normally one at most). */
    List<User> findByDeviceToken(DeviceToken deviceToken);

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);
}
