package com.innovify.skillswap.learningpathengine.interfaces.rest.resources;

import java.util.List;

/**
 * One step of a learning path: a skill the student has to demonstrate.
 *
 * @param id                    the unique identifier of the node
 * @param skillTag              the skill code in the taxonomy
 * @param skillName             the display name of the skill
 * @param order                 position of the node in the path (1 = first)
 * @param status                Locked, Available or Completed
 * @param prerequisiteSkillTags skills of this path that must be completed first
 * @param linkedCertificateId   certificate linked as supporting evidence, if any. It never completes the node.
 * @param assessmentBlueprintId latest assessment generated for the node, if any
 */
public record PathNodeResource(
        int id,
        String skillTag,
        String skillName,
        int order,
        String status,
        List<String> prerequisiteSkillTags,
        Integer linkedCertificateId,
        Integer assessmentBlueprintId) {
}
