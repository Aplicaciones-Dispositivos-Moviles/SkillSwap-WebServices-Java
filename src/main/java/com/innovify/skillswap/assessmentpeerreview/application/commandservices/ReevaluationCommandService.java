package com.innovify.skillswap.assessmentpeerreview.application.commandservices;

import com.innovify.skillswap.assessmentpeerreview.domain.model.aggregates.Reevaluation;
import com.innovify.skillswap.assessmentpeerreview.domain.model.commands.OrderReevaluationCommand;
import com.innovify.skillswap.shared.application.Result;

/** Reevaluation command service interface. */
public interface ReevaluationCommandService {

    /**
     * A Verificador senior orders the reevaluation of an approved attempt or case (US37). The node goes back to
     * available (its dependents stay unlocked), the path is active again, and an approved case is reopened for another
     * verifier. The justification is kept. Fails with NOT_SENIOR_VERIFIER, INVALID_SOURCE_TYPE, JUSTIFICATION_REQUIRED,
     * JUSTIFICATION_TOO_LONG, ATTEMPT_NOT_FOUND, BLUEPRINT_NOT_FOUND, CASE_NOT_FOUND, CANNOT_REEVALUATE_OWN_WORK,
     * ATTEMPT_NOT_PASSED, CASE_NOT_APPROVED, OPEN_CASE_ALREADY_EXISTS or NODE_NOT_REOPENABLE.
     */
    Result<Reevaluation> handle(OrderReevaluationCommand command);
}
