package com.innovify.skillswap.recognitionincentives.domain.services;

import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.Credits;
import com.innovify.skillswap.recognitionincentives.domain.model.valueobjects.RedemptionItem;
import com.innovify.skillswap.shared.domain.exceptions.DomainException;

/** Advanced path unlock = 50 SkillCredits; contribution certificate = 30 SkillCredits. */
public class DefaultRedemptionPricing implements RedemptionPricing {

    public static final int ADVANCED_PATH_UNLOCK_COST = 50;
    public static final int CONTRIBUTION_CERTIFICATE_COST = 30;

    @Override
    public Credits calculateCost(RedemptionItem item) {
        if (item == null) {
            throw new DomainException("The benefit to redeem is not valid.");
        }
        return switch (item) {
            case ADVANCED_PATH_UNLOCK -> new Credits(ADVANCED_PATH_UNLOCK_COST);
            case CONTRIBUTION_CERTIFICATE -> new Credits(CONTRIBUTION_CERTIFICATE_COST);
        };
    }
}
