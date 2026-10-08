package com.innovify.skillswap.iam.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Spring Data access to the "users" table. Only {@link UserRepositoryAdapter} uses it. */
public interface UserJpaRepository extends JpaRepository<User, Integer> {

    Optional<User> findByUsername(Username username);

    Optional<User> findByEmail(Email email);

    boolean existsByUsername(Username username);

    boolean existsByEmail(Email email);
}
