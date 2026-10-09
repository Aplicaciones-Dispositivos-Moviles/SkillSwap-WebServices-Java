package com.innovify.skillswap.recognitionincentives.domain.services;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;

/** Contract for the cost in SkillCredits of each benefit. */
public interface RedemptionPricing {

    Credits calculateCost(RedemptionItem item);
}
