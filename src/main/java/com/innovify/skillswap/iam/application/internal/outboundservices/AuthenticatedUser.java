package com.innovify.skillswap.iam.application.internal.outboundservices;

import com.innovify.skillswap.iam.domain.model.aggregates.User;

/** Result of a successful sign in: the user and the JWT issued for the session. */
public record AuthenticatedUser(User user, String token) {
}
