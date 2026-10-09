package com.innovify.skillswap.moderationdisputes.application.commandservices;

import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.AssignPendingDisputesCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.EscalateCertificateReviewCommand;
import com.innovify.skillswap.moderationdisputes.domain.model.commands.ResolveDisputeCommand;
import com.innovify.skillswap.shared.application.Result;

/** Dispute command service interface. */
public interface DisputeCommandService {

    /**
     * Opens the review of a suspicious certificate and assigns it to the least loaded available Verificador senior;
     * when there is none, to the least loaded available verifier (never the owner of the certificate). With nobody
     * available it waits as pending without a reviewer. Escalating the same certificate again answers the dispute
     * already opened.
     */
    Result<Dispute> handle(EscalateCertificateReviewCommand command);

    /**
     * The assigned reviewer resolves the dispute with an outcome and their observations; for a certificate review,
     * Upheld verifies the certificate and Overturned rejects it, in the same transaction.
     */
    Result<Dispute> handle(ResolveDisputeCommand command);

    /** Assigns the disputes still waiting for a reviewer. @return how many were assigned */
    Result<Integer> handle(AssignPendingDisputesCommand command);
}
