package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.valueobjects.Email;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** In-memory repository that assigns ids like the database does. */
public class FakeUserRepository implements UserRepository {

    private final List<User> users = new ArrayList<>();
    private int nextId = 1;
    private int saveCalls;
    private RuntimeException saveFailure;

    public List<User> users() {
        return users;
    }

    public int saveCalls() {
        return saveCalls;
    }

    /** Makes every following save throw the given exception. */
    public void failOnSave(RuntimeException failure) {
        this.saveFailure = failure;
    }

    @Override
    public User save(User user) {
        saveCalls++;
        if (saveFailure != null) {
            throw saveFailure;
        }
        if (user.getId() == null) {
            ReflectionTestUtils.setField(user, "id", nextId++);
            users.add(user);
        }
        return user;
    }

    @Override
    public Optional<User> findById(int id) {
        return users.stream().filter(user -> Objects.equals(user.getId(), id)).findFirst();
    }

    @Override
    public Optional<User> findByUsername(Username username) {
        return users.stream().filter(user -> user.getUsername().equals(username)).findFirst();
    }

    @Override
    public Optional<User> findByEmail(Email email) {
        return users.stream().filter(user -> user.getEmail().equals(email)).findFirst();
    }

    @Override
    public boolean existsByUsername(Username username) {
        return findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(Email email) {
        return findByEmail(email).isPresent();
    }
}
