package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.entities.PathNode;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import java.util.List;

/** Builds the ordered nodes of a path from a skill gap, respecting the prerequisites between skills. */
public interface LearningPathBuilder {

    List<PathNode> buildPath(SkillGap gap);
}
