package com.innovify.skillswap.reputation.application.acl;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Anti-corruption facade through which other bounded contexts (Assessment &amp; Peer Review, Moderation &amp;
 * Disputes) ask who is a Verificador senior, without depending on the reliability aggregate.
 */
public interface ReputationContextFacade {

    /** Whether the user is a Verificador senior right now: Gold rank and a reliability of 90 or more. */
    boolean isSeniorVerifier(int userId);

    /** The reliability of the verifier from 0 to 100; empty while Reputation has no record of them. */
    OptionalInt reliabilityOf(int userId);

    /** Which of those users are Verificadores senior right now. */
    Set<Integer> findSeniorVerifiers(Collection<Integer> userIds);
}
