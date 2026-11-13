package com.innovify.skillswap.iam.application.fakes;

import com.innovify.skillswap.iam.application.internal.outboundservices.TokenGenerator;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import java.util.Optional;

public class FakeTokenGenerator implements TokenGenerator {

    private static final String PREFIX = "token-for-";

    @Override
    public String generateToken(User user) {
        return PREFIX + user.getId();
    }

    @Override
    public Optional<Integer> validateToken(String token) {
        if (token == null || !token.startsWith(PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(Integer.parseInt(token.substring(PREFIX.length())));
    }
}
