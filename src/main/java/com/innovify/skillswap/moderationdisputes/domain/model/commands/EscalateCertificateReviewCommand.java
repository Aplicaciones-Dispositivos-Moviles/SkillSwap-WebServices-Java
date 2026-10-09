package com.innovify.skillswap.moderationdisputes.domain.model.commands;

import java.util.List;

/**
 * Escalates a suspicious certificate to a Verificador senior. Escalating the same certificate again changes nothing.
 *
 * @param certificateId the certificate
 * @param ownerId       the student who registered it, who never reviews it
 * @param reasons       the rules that made it suspicious
 */
public record EscalateCertificateReviewCommand(int certificateId, int ownerId, List<String> reasons) {

    public EscalateCertificateReviewCommand {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
