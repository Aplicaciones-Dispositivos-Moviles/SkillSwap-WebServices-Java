package com.innovify.skillswap.iam.application.commandservices;

import com.innovify.skillswap.iam.application.internal.outboundservices.AuthenticatedUser;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.commands.SignInCommand;
import com.innovify.skillswap.iam.domain.model.commands.SignUpCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserBioCommand;
import com.innovify.skillswap.iam.domain.model.commands.UpdateUserFullNameCommand;
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
     * Handle update user full name command: the real name the holder of the certificates is compared with. A blank
     * name clears it. @return the updated user
     */
    Result<User> handle(UpdateUserFullNameCommand command);
}
