package com.innovify.skillswap.learningpathengine.interfaces.rest.transform;

import com.innovify.skillswap.learningpathengine.domain.model.commands.LinkCertificateToNodeCommand;
import com.innovify.skillswap.learningpathengine.interfaces.rest.resources.LinkCertificateResource;

public final class LinkCertificateToNodeCommandFromResourceAssembler {

    private LinkCertificateToNodeCommandFromResourceAssembler() {
    }

    /**
     * The student is always the authenticated user. A missing certificate id becomes 0, which no certificate has,
     * so it is answered as not found.
     */
    public static LinkCertificateToNodeCommand toCommandFromResource(int pathNodeId, LinkCertificateResource resource,
                                                                     int studentId) {
        int certificateId = resource == null || resource.certificateId() == null ? 0 : resource.certificateId();
        return new LinkCertificateToNodeCommand(pathNodeId, studentId, certificateId);
    }
}
