package com.innovify.skillswap.iam.infrastructure.persistence.jpa.repositories;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Implements the domain {@link UserRepository} port on top of Spring Data JPA. */
@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    public UserRepositoryAdapter(UserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public User save(User user) {
        return jpaRepository.saveAndFlush(user);
    }

    @Override
    public Optional<User> findById(int id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return jpaRepository.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public boolean existsByUsername(Username username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(Email email) {
        return jpaRepository.existsByEmail(email);
    }
}
