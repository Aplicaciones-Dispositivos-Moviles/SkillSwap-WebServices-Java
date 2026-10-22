package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.application.queryservices.SkillOption;
import com.innovify.skillswap.learningpathengine.domain.model.queries.GetSkillOptionsQuery;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.GetSkillOptionsResource;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.SkillOptionResource;

public final class SkillOptionResourceFromOptionAssembler {

    private SkillOptionResourceFromOptionAssembler() {
    }

    /** The student is always the authenticated user, never a value from the request body. */
    public static GetSkillOptionsQuery toQueryFromResource(GetSkillOptionsResource resource, int studentId) {
        return new GetSkillOptionsQuery(studentId, resource == null || resource.goal() == null ? "" : resource.goal());
    }

    public static SkillOptionResource toResourceFromOption(SkillOption option) {
        return new SkillOptionResource(option.skillTag(), option.skillName(), option.description(),
                option.nodeCount(), option.estimatedWeeks(), option.affinity());
    }
}
