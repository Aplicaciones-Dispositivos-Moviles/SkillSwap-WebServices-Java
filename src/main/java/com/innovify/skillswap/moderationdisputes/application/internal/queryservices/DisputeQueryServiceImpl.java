package com.innovify.skillswap.moderationdisputes.application.internal.queryservices;

import com.innovify.skillswap.credentialverification.application.acl.CertificateReviewView;
import com.innovify.skillswap.credentialverification.application.acl.CredentialContextFacade;
import com.innovify.skillswap.moderationdisputes.application.queryservices.DisputeQueryService;
import com.innovify.skillswap.moderationdisputes.domain.model.aggregates.Dispute;
import com.innovify.skillswap.moderationdisputes.domain.model.queries.GetDisputeByIdQuery;
import com.innovify.skillswap.moderationdisputes.domain.model.queries.GetDisputesByReviewerQuery;
import com.innovify.skillswap.moderationdisputes.domain.model.valueobjects.DisputeSourceType;
import com.innovify.skillswap.moderationdisputes.domain.repositories.DisputeRepository;
import java.util.List;
import java.util.Optional;

public class DisputeQueryServiceImpl implements DisputeQueryService {

    private final DisputeRepository disputeRepository;
    private final CredentialContextFacade credentialFacade;

    public DisputeQueryServiceImpl(DisputeRepository disputeRepository, CredentialContextFacade credentialFacade) {
        this.disputeRepository = disputeRepository;
        this.credentialFacade = credentialFacade;
    }

    @Override
    public Optional<Dispute> handle(GetDisputeByIdQuery query) {
        return disputeRepository.findById(query.disputeId());
    }

    @Override
    public List<Dispute> handle(GetDisputesByReviewerQuery query) {
        return disputeRepository.findByAssignedVerifier(query.verifierUserId(), query.status());
    }

    @Override
    public Optional<CertificateReviewView> getCertificateEvidence(Dispute dispute) {
        if (dispute.getSourceType() != DisputeSourceType.CERTIFICATE_REVIEW) {
            return Optional.empty();
        }
        return credentialFacade.getCertificateForReview(dispute.getSourceReferenceId());
    }
}
