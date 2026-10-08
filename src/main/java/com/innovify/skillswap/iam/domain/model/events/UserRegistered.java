package com.innovify.skillswap.iam.domain.model.events;

import com.innovify.skillswap.iam.domain.model.valueobjects.UserRole;
import com.innovify.skillswap.shared.domain.events.DomainEvent;

/**
 * A new account was registered.
 *
 * @param userId the new user
 * @param role   the role of the account
 */
public record UserRegistered(int userId, UserRole role) implements DomainEvent {
}
