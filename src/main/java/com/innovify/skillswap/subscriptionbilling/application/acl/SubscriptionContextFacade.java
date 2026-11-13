package com.innovify.skillswap.subscriptionbilling.application.acl;

/**
 * Anti-corruption facade through which other bounded contexts (Learning Path Engine, Assessment &amp; Peer Review)
 * read the plan of a student, without depending on the subscriptions or the payment gateway.
 */
public interface SubscriptionContextFacade {

    /** The limits of the plan the student is on right now; the free plan when they have no paid one. */
    PlanLimitsView getPlanLimits(int studentId);
}
