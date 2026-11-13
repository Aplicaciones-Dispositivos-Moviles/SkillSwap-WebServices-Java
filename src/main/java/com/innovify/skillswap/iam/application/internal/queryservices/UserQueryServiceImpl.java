package com.innovify.skillswap.iam.application.internal.queryservices;

import com.innovify.skillswap.iam.application.queryservices.UserQueryService;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByIdQuery;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByUsernameQuery;
import com.innovify.skillswap.iam.domain.model.valueobjects.Username;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserRepository userRepository;

    public UserQueryServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> handle(GetUserByIdQuery query) {
        return userRepository.findById(query.userId());
    }

    @Override
    public Optional<User> handle(GetUserByUsernameQuery query) {
        return Username.isValid(query.username())
                ? userRepository.findByUsername(new Username(query.username()))
                : Optional.empty();
    }
}
