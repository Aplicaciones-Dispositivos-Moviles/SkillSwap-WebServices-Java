package com.innovify.skillswap.credentialverification.application.fakes;

import com.innovify.skillswap.iam.application.acl.IamContextFacade;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** The registered names of the users, set by each test. */
public class FakeIamContextFacade implements IamContextFacade {

    private final Map<Integer, String> fullNames = new HashMap<>();

    public FakeIamContextFacade withFullName(int userId, String fullName) {
        fullNames.put(userId, fullName);
        return this;
    }

    @Override
    public Optional<String> getRegisteredFullName(int userId) {
        return Optional.ofNullable(fullNames.get(userId));
    }
}
