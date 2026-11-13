package com.innovify.skillswap.subscriptionbilling.domain.model.valueobjects;

/** The plan a student is on right now: the free one, or the paid monthly one. */
public enum PlanType {
    FREE("Free"),
    PREMIUM("Premium");

    private final String value;

    PlanType(String value) {
        this.value = value;
    }

    /** The representation exposed by the API. */
    public String value() {
        return value;
    }
}
