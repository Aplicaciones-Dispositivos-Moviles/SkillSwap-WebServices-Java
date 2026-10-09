package com.innovify.skillswap.iam.application.commandservices;

import com.innovify.skillswap.iam.application.internal.outboundservices.AuthenticatedUser;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.RegisterDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.RemoveDeviceTokenCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.shared.application.Result;

/** User command service interface. */
public interface UserCommandService {

    /** Handle sign up command. @return the created user */
    Result<User> handle(SignUpCommand command);

    /** Handle sign in command. @return the authenticated user and its JWT */
    Result<AuthenticatedUser> handle(SignInCommand command);

    /** Handle update user bio command. @return the updated user */
    Result<User> handle(UpdateUserBioCommand command);

    /**
     * Handle register device token command: the device of the user receives the push notifications from now on.
     * A token registered before by another account (a shared device) is removed from it.
     */
    Result<User> handle(RegisterDeviceTokenCommand command);

    /** Handle remove device token command: no push notification is sent to the user any more. */
    Result<User> handle(RemoveDeviceTokenCommand command);
}
