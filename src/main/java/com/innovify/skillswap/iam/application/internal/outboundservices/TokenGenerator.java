package com.innovify.skillswap.iam.application.internal.outboundservices;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import java.util.Optional;

/** Contract for generating and validating the access tokens of authenticated sessions. */
public interface TokenGenerator {

    /** Generate a token encoding the user id and role. */
    String generateToken(User user);

    /** @return the user id encoded in the token if it is valid; otherwise empty */
    Optional<Integer> validateToken(String token);
}
