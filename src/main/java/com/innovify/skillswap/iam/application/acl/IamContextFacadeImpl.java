package com.innovify.skillswap.iam.application.acl;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.repositories.UserRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class IamContextFacadeImpl implements IamContextFacade {

    private final UserRepository userRepository;

    public IamContextFacadeImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<String> getRegisteredFullName(int userId) {
        return userRepository.findById(userId).map(User::getFullName);
    }
}
