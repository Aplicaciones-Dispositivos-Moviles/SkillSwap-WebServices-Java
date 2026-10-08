package com.innovify.skillswap.learningpathengine.domain.services;

import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.CareerGoal;
import com.innovify.skillswap.learningpathengine.domain.model.valueobjects.SkillGap;
import java.util.Collection;

/** Compares what a student already demonstrated against what their goal requires. */
public interface SkillGapAnalyzer {

    SkillGap analyze(CareerGoal goal, Collection<String> verifiedSkillTags);
}
