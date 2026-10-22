package com.innovify.skillswap.recognitionincentives.application.internal.queryservices;

import com.innovify.skillswap.recognitionincentives.application.internal.CredentialViews;
import com.innovify.skillswap.recognitionincentives.application.queryservices.PublicCredentialView;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialQueryService;
import com.innovify.skillswap.recognitionincentives.application.queryservices.ShareableCredentialView;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetPublicCredentialByTokenQuery;
import com.innovify.skillswap.recognitionincentives.domain.model.queries.GetShareableCredentialsByOwnerQuery;
import com.innovify.skillswap.recognitionincentives.domain.repositories.ShareableCredentialRepository;
import java.util.List;
import java.util.Optional;

public class ShareableCredentialQueryServiceImpl implements ShareableCredentialQueryService {

    private final ShareableCredentialRepository credentials;
    private final CredentialViews views;

    public ShareableCredentialQueryServiceImpl(ShareableCredentialRepository credentials, CredentialViews views) {
        this.credentials = credentials;
        this.views = views;
    }

    @Override
    public Optional<PublicCredentialView> handle(GetPublicCredentialByTokenQuery query) {
        if (query.token() == null || query.token().isBlank()) {
            return Optional.empty();
        }
        return credentials.findByToken(query.token().strip()).map(views::toPublicView);
    }

    @Override
    public List<ShareableCredentialView> handle(GetShareableCredentialsByOwnerQuery query) {
        return views.toViews(query.ownerId(), credentials.findByOwnerId(query.ownerId()));
    }
}
