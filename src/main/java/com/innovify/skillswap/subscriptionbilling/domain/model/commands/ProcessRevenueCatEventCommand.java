package com.innovify.skillswap.subscriptionbilling.domain.model.commands;

import java.util.List;

/**
 * A webhook notification of RevenueCat. Only its id, type and user matter: the state it announces is read again
 * from the gateway, as RevenueCat recommends, instead of trusting the payload.
 *
 * @param eventId           the id of the event, the same in every retry
 * @param type              TEST, INITIAL_PURCHASE, RENEWAL, CANCELLATION, UNCANCELLATION, EXPIRATION...
 * @param appUserId         the app user id (the id of the student)
 * @param originalAppUserId the first app user id of the customer, if any
 * @param aliases           the other app user ids of the customer, if any
 * @param environment       SANDBOX or PRODUCTION
 */
public record ProcessRevenueCatEventCommand(String eventId, String type, String appUserId,
                                            String originalAppUserId, List<String> aliases, String environment) {

    public ProcessRevenueCatEventCommand {
        aliases = aliases == null ? List.of() : aliases.stream().filter(alias -> alias != null).toList();
    }
}
