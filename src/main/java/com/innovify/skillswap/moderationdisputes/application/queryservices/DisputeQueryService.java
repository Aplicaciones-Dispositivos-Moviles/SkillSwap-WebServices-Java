package com.innovify.skillswap.moderationdisputes.application.queryservices;

import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewView;
import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.queries.GetDisputeByIdQuery;
import com.innovify.skillswap.moderationdisputes.domain.model.queries.GetDisputesByReviewerQuery;
import java.util.List;
import java.util.Optional;

/** Dispute query service interface. */
public interface DisputeQueryService {

    Optional<Dispute> handle(GetDisputeByIdQuery query);

    /** The disputes assigned to the reviewer, oldest first. */
    List<Dispute> handle(GetDisputesByReviewerQuery query);

    /** The certificate under review, read from Credential Verification; empty for other origins. */
    Optional<CertificateReviewView> getCertificateEvidence(Dispute dispute);
}
