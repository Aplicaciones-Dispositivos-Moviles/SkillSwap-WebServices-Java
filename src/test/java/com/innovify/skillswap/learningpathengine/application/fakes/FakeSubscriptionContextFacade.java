package com.innovify.skillswap.learningpathengine.application.fakes;

import com.innovify.skillswap.subscriptionbilling.application.acl.PlanLimitsView;
import com.innovify.skillswap.subscriptionbilling.application.acl.SubscriptionContextFacade;
import java.util.HashMap;
import java.util.Map;

/** Every student is on the free plan unless a test puts them on the paid one. */
public class FakeSubscriptionContextFacade implements SubscriptionContextFacade {

    public static final PlanLimitsView FREE = new PlanLimitsView("Free", 1, 3, 3, null, 5);
    public static final PlanLimitsView PREMIUM = new PlanLimitsView("Premium", 3, null, 10, 48, null);

    private final Map<Integer, PlanLimitsView> plans = new HashMap<>();

    public FakeSubscriptionContextFacade premium(int studentId) {
        plans.put(studentId, PREMIUM);
        return this;
    }

    public FakeSubscriptionContextFacade free(int studentId) {
        plans.put(studentId, FREE);
        return this;
    }

    @Override
    public PlanLimitsView getPlanLimits(int studentId) {
        return plans.getOrDefault(studentId, FREE);
    }
}
