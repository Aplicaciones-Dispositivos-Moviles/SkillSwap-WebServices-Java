package com.innovify.skillswap.reputation.application.acl;

import java.util.Collection;
import java.util.Set;

/**
 * Anti-corruption facade through which other bounded contexts (Assessment &amp; Peer Review, Moderation &amp;
 * Disputes) ask who is a Verificador senior, without depending on the reliability aggregate.
 */
public interface ReputationContextFacade {

    /** Whether the user is a Verificador senior right now: Gold rank and a reliability of 90 or more. */
    boolean isSeniorVerifier(int userId);

    /** Which of those users are Verificadores senior right now. */
    Set<Integer> findSeniorVerifiers(Collection<Integer> userIds);
}
