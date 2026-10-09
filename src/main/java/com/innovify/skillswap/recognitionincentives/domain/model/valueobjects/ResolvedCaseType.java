package com.innovify.skillswap.recognitionincentives.domain.model.valueobjects;

/**
 * The work a verifier reviewed in a resolved case, as this context sees it: it sets how much the case pays. It
 * is translated from the event of Assessment &amp; Peer Review, so the rewards do not depend on that model.
 */
public enum ResolvedCaseType {
    QUIZ,
    MINI_PROJECT
}
