package com.innovify.skillswap.iam.application.queryservices;

import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByIdQuery;
import com.innovify.skillswap.iam.domain.model.queries.GetUserByUsernameQuery;
import java.util.Optional;

/** User query service interface. */
public interface UserQueryService {

    Optional<User> handle(GetUserByIdQuery query);

    Optional<User> handle(GetUserByUsernameQuery query);
}
