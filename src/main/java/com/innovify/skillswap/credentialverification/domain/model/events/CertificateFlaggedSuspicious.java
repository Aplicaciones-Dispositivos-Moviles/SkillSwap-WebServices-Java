package com.innovify.skillswap.credentialverification.domain.model.events;

import com.innovify.skillswap.shared.domain.events.DomainEvent;
import java.util.List;

/**
 * A certificate was registered as suspicious and must be reviewed by a person: Moderation &amp; Disputes escalates
 * it to a Verificador senior.
 *
 * @param certificateId the certificate
 * @param ownerId       the student who registered it, who can never review it
 * @param reasons       why it is suspicious: DuplicateFile, DuplicateCertificateNumber, DuplicateVerificationCode,
 *                      HolderNameMismatch and/or OcrInconsistencies
 */
public record CertificateFlaggedSuspicious(int certificateId, int ownerId, List<String> reasons)
        implements DomainEvent {

    public CertificateFlaggedSuspicious {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
