package com.innovify.skillswap.credentialverification.interfaces.rest;

import com.innovify.skillswap.credentialverification.application.commandservices.CertificateCommandService;
import com.innovify.skillswap.credentialverification.application.queryservices.CertificateQueryService;
import com.innovify.skillswap.credentialverification.domain.model.CredentialVerificationError;
import com.innovify.skillswap.credentialverification.domain.model.aggregates.Certificate;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificateByIdQuery;
import com.innovify.skillswap.credentialverification.domain.model.queries.GetCertificatesByOwnerIdQuery;
import com.innovify.skillswap.credentialverification.interfaces.rest.resources.CertificateResource;
import com.innovify.skillswap.credentialverification.interfaces.rest.resources.UploadCertificateResource;
import com.innovify.skillswap.credentialverification.interfaces.rest.transform.CertificateResourceFromEntityAssembler;
import com.innovify.skillswap.credentialverification.interfaces.rest.transform.CredentialVerificationActionResultAssembler;
import com.innovify.skillswap.credentialverification.interfaces.rest.transform.UploadCertificateCommandFromResourceAssembler;
import com.innovify.skillswap.iam.domain.model.aggregates.User;
import com.innovify.skillswap.shared.domain.errors.ErrorCodes;
import java.net.URI;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Certificates. Every endpoint needs a valid token (see SecurityConfig). The file limit is 10 MB (checked by the
 * application layer with a friendly error); the larger multipart limit in application.properties only stops
 * clearly abusive uploads.
 */
@RestController
@RequestMapping("/api/v1/certificates")
public class CertificatesController {

    private final CertificateCommandService commandService;
    private final CertificateQueryService queryService;
    private final MessageSource messageSource;

    public CertificatesController(CertificateCommandService commandService, CertificateQueryService queryService,
                                  MessageSource messageSource) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.messageSource = messageSource;
    }

    /**
     * Uploads a certificate file (JPG, PNG or PDF, up to 10 MB) with the fields read by the on-device OCR. The
     * owner is the authenticated student. 201 with the certificate; 400, 409, 413 or 415 on invalid uploads.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> uploadCertificate(@ModelAttribute UploadCertificateResource resource,
                                               @AuthenticationPrincipal User actor) {
        var command = UploadCertificateCommandFromResourceAssembler.toCommandFromResource(resource, actor.getId());
        var result = commandService.handle(command);

        return CredentialVerificationActionResultAssembler.toResponse(result,
                certificate -> ResponseEntity.created(URI.create("/api/v1/certificates/" + certificate.getId()))
                        .body(toResource(certificate)));
    }

    /** The detail of a certificate. Only its owner can see it. */
    @GetMapping("/{id:\\d+}")
    public ResponseEntity<?> getCertificateById(@PathVariable int id, @AuthenticationPrincipal User actor) {
        var certificate = queryService.handle(new GetCertificateByIdQuery(id));
        if (certificate.isEmpty()) {
            return error(CredentialVerificationError.CERTIFICATE_NOT_FOUND);
        }
        if (!canAccess(actor, certificate.get().getOwnerId())) {
            return error(CredentialVerificationError.NOT_CERTIFICATE_OWNER);
        }
        return ResponseEntity.ok(toResource(certificate.get()));
    }

    /**
     * The certificates of the authenticated student, newest first. {@code ownerId} is optional and must be the
     * student's own id: a certificate belongs to its owner and nobody else lists it.
     */
    @GetMapping
    public ResponseEntity<?> getCertificatesByOwner(@RequestParam(required = false) Integer ownerId,
                                                    @AuthenticationPrincipal User actor) {
        int targetId = ownerId == null ? actor.getId() : ownerId;
        if (!canAccess(actor, targetId)) {
            return error(CredentialVerificationError.NOT_CERTIFICATE_OWNER);
        }
        var resources = queryService.handle(new GetCertificatesByOwnerIdQuery(targetId)).stream()
                .map(this::toResource)
                .toList();
        return ResponseEntity.ok(resources);
    }

    /** A certificate can only be read by its owner. */
    private static boolean canAccess(User actor, int ownerId) {
        return actor.getId() == ownerId;
    }

    private CertificateResource toResource(Certificate certificate) {
        return CertificateResourceFromEntityAssembler.toResourceFromEntity(certificate,
                queryService.getFileUrl(certificate));
    }

    private ResponseEntity<?> error(CredentialVerificationError error) {
        String code = ErrorCodes.of(error);
        String message = messageSource.getMessage(code, null, code, LocaleContextHolder.getLocale());
        return CredentialVerificationActionResultAssembler.toError(error, message);
    }
}
