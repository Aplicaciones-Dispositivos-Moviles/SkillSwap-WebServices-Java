package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

/**
 * The failed attempt was not escalated to a verifier because the student used every escalation of the month that
 * their plan allows. The attempt itself is recorded.
 *
 * @param plan    Free or Premium
 * @param max     escalations a month the plan allows
 * @param current cases the student opened this calendar month
 */
public record EscalationLimitReached(String plan, int max, int current) {

    /** Whether the paid plan would allow more escalations. */
    public boolean upgradeAvailable() {
        return "Free".equals(plan);
    }
}
