package com.innovify.skillswap.learningpathengine.application.queryservices;

import com.innovify.skillswap.learningpathengine.domain.model.queries.GetSkillOptionsQuery;
import com.innovify.skillswap.shared.application.Result;
import java.util.List;

/** Skill options query service interface. */
public interface SkillOptionsQueryService {

    /**
     * The skills of the catalog that fit the goal, the best fit first (at most 5, affinity of 0.3 or more). A skill the
     * student already demonstrated entirely is not offered. Fails with INVALID_GOAL (empty or longer than 500
     * characters), GOAL_NOT_INTERPRETABLE (no skill fits), GOAL_ALREADY_ACHIEVED (every skill that fits was already
     * demonstrated), DATABASE_ERROR or INTERNAL_SERVER_ERROR.
     */
    Result<List<SkillOption>> handle(GetSkillOptionsQuery query);
}
