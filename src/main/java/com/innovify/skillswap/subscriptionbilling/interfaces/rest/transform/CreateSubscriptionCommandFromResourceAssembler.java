package com.innovify.skillswap.subscriptionbilling.interfaces.rest.transform;

import com.innovify.skillswap.subscriptionbilling.domain.model.commands.CreateSubscriptionCommand;
import com.innovify.skillswap.subscriptionbilling.interfaces.rest.resources.CreateSubscriptionResource;

public final class CreateSubscriptionCommandFromResourceAssembler {

    private CreateSubscriptionCommandFromResourceAssembler() {
    }

    /** The student is always the authenticated user, never a value from the request body. */
    public static CreateSubscriptionCommand toCommandFromResource(CreateSubscriptionResource resource, int studentId) {
        return new CreateSubscriptionCommand(studentId, resource == null ? null : resource.productId());
    }
}
