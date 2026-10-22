package com.innovify.skillswap.recognitionincentives.application.queryservices;

import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetPublicCredentialByTokenQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetShareableCredentialsByOwnerQuery;
import java.util.List;
import java.util.Optional;

/** Shareable credential query service interface. */
public interface ShareableCredentialQueryService {

    /** What anyone sees with the link, or empty when the token does not exist. */
    Optional<PublicCredentialView> handle(GetPublicCredentialByTokenQuery query);

    /** The credentials of the student, the newest first. */
    List<ShareableCredentialView> handle(GetShareableCredentialsByOwnerQuery query);
}
